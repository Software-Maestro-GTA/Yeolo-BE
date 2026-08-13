package com.soma.yeolo.place.client;

import static com.soma.yeolo.global.client.JsonNodes.number;
import static com.soma.yeolo.global.client.JsonNodes.text;
import static com.soma.yeolo.global.client.JsonNodes.textList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Google Places API (New) 기반 장소 조회. ({@code place.provider=google}일 때 활성화)
 *
 * <p>Text Search({@code places:searchText})를 <b>한 번만</b> 호출한다. 구 Places API는 검색과 상세를
 * 나눠 두 번 불러야 했지만, 신 API는 FieldMask로 요청한 필드를 한 응답에 담아주므로 운영시간까지
 * 같이 받는다. 구 API는 2025-03 이후 생성된 프로젝트에서 활성화가 불가능해 선택지도 아니다.
 *
 * <p><b>FieldMask는 필요한 필드만 지정한다.</b> 신 API는 요청한 필드에 따라 과금 티어가 갈려서,
 * {@code places.*}처럼 전체를 요청하면 최상위 티어로 청구된다.
 *
 * <p><b>사진은 내려보내지 않는다({@code photoUrl}은 항상 null).</b> Places의 사진 URL은 API 키를
 * 요구해서, 그대로 FE에 주면 키가 노출된다. 사진을 제공하려면 BE 이미지 프록시가 필요하며 이번
 * 범위 밖이다. (명세 개정으로 사진은 목록이 아니라 단일 {@code photoUrl}이 됐고, 코스 생성
 * 경로에서는 AI가 준 사진 URL이 대신 저장된다 — {@code ItineraryPlaceNormalizer}.)
 *
 * <p><b>영문명도 내려보내지 않는다({@code placeEngName}은 항상 null).</b> Text Search는 요청한 언어
 * 하나로만 이름을 주므로, 한국어명과 영문명을 함께 얻으려면 같은 장소를 두 번 조회해야 한다.
 *
 * <p>조회 실패는 포트 계약대로 예외 대신 빈 값으로 돌려준다. (docs/architecture.md §5)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "place.provider", havingValue = "google")
public class GooglePlaceLookupClient implements PlaceLookupClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";

    /** 응답에 담아올 필드 — 필요한 것만 지정해 호출 비용(티어)을 줄인다. */
    private static final String FIELD_MASK = String.join(",",
            "places.id",
            "places.displayName",
            "places.formattedAddress",
            "places.location",
            "places.rating",
            "places.primaryType",
            "places.types",
            "places.regularOpeningHours.weekdayDescriptions");

    private final RestClient restClient;
    private final GooglePlaceProperties properties;

    public GooglePlaceLookupClient(@Qualifier("restClient") RestClient restClient,
                                   GooglePlaceProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Optional<Place> lookup(PlaceQuery query) {
        try {
            return searchFirstResult(query).flatMap(place -> toPlace(place, query));
        } catch (RestClientResponseException e) {
            // 키 미설정·API 미활성화(403)가 여기로 온다 — 본문에 원인이 담겨 있어 함께 남긴다.
            log.warn("Google Places 검색 거부 - '{}': {} {}",
                    query.searchText(), e.getStatusCode(), e.getResponseBodyAsString());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Google Places 검색 실패 - '{}': {}", query.searchText(), e.toString());
            return Optional.empty();
        }
    }

    /** Text Search 호출 후 최상위 결과 1건을 반환한다. 결과가 없으면 빈 값. */
    private Optional<JsonNode> searchFirstResult(PlaceQuery query) throws Exception {
        ObjectNode body = OBJECT_MAPPER.createObjectNode()
                .put("textQuery", query.searchText())
                .put("maxResultCount", 1);
        if (properties.language() != null && !properties.language().isBlank()) {
            body.put("languageCode", properties.language());
        }

        String response = restClient.post()
                .uri(properties.searchTextUrl())
                .header(API_KEY_HEADER, properties.apiKey())
                .header(FIELD_MASK_HEADER, FIELD_MASK)
                .contentType(MediaType.APPLICATION_JSON)
                .body(OBJECT_MAPPER.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        // 결과가 없으면 신 API는 에러가 아니라 빈 객체({})를 준다.
        JsonNode places = OBJECT_MAPPER.readTree(response).path("places");
        return (places.isArray() && !places.isEmpty())
                ? Optional.of(places.get(0)) : Optional.empty();
    }

    private Optional<Place> toPlace(JsonNode result, PlaceQuery query) {
        String providerPlaceId = text(result, "id");
        JsonNode location = result.path("location");
        Double latitude = number(location, "latitude");
        Double longitude = number(location, "longitude");
        // 좌표나 식별자가 없는 결과는 저장할 수 없다(Place의 불변식) — 미정규화로 남긴다.
        if (providerPlaceId == null || latitude == null || longitude == null) {
            log.debug("Google Places 결과에 식별자/좌표가 없다 - '{}'", query.searchText());
            return Optional.empty();
        }
        String displayName = text(result.path("displayName"), "text");
        return Optional.of(new Place(
                providerPlaceId,
                // 이름이 없으면 AI가 준 장소명을 그대로 유지한다.
                displayName != null ? displayName : query.placeName(),
                null,
                category(result),
                text(result, "formattedAddress"),
                latitude,
                longitude,
                number(result, "rating"),
                null,
                textList(result.path("regularOpeningHours"), "weekdayDescriptions")
        ));
    }

    /**
     * 분류는 신 API가 직접 주는 {@code primaryType}(가장 구체적인 유형)을 쓰고, 없으면 {@code types}의
     * 첫 값으로 폴백한다 — 구 API에서 {@code types[0]}를 쓰던 것과 같은 의미를 유지한다.
     */
    private String category(JsonNode result) {
        String primaryType = text(result, "primaryType");
        if (primaryType != null) {
            return primaryType;
        }
        List<String> types = textList(result, "types");
        return types.isEmpty() ? null : types.get(0);
    }
}
