package com.soma.yeolo.place.client;

import static com.soma.yeolo.global.client.JsonNodes.number;
import static com.soma.yeolo.global.client.JsonNodes.text;
import static com.soma.yeolo.global.client.JsonNodes.textList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google Places 기반 장소 조회. ({@code place.provider=google}일 때 활성화)
 *
 * <p>두 단계로 호출한다. Text Search로 장소명 → 후보 1건(장소 식별자·좌표·주소·평점·유형)을 얻고,
 * Place Details로 운영시간({@code opening_hours.weekday_text})만 보강한다. Details가 실패해도
 * Text Search 결과만으로 장소를 돌려준다 — 운영시간은 부가 정보라 조회 자체를 실패시킬 이유가 없다.
 *
 * <p><b>사진은 내려보내지 않는다({@code photoUrls}는 항상 빈 목록).</b> Places의 사진 URL은 API 키를
 * 쿼리 파라미터로 요구해서, 그대로 FE에 주면 키가 노출된다. 사진을 제공하려면 BE 이미지 프록시가
 * 필요하며 이번 범위 밖이다. (API-PLACE-1의 "사진 없음 → 빈 배열" 처리로 계약은 만족한다.)
 *
 * <p>조회 실패는 포트 계약대로 예외 대신 빈 값으로 돌려준다. (docs/architecture.md §5)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "place.provider", havingValue = "google")
public class GooglePlaceLookupClient implements PlaceLookupClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Details에서 받아올 필드 — 필요한 것만 지정해 호출 비용을 줄인다. */
    private static final String DETAIL_FIELDS = "opening_hours/weekday_text";

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
            return searchFirstResult(query).flatMap(result -> toPlace(result, query));
        } catch (Exception e) {
            log.warn("Google Places 검색 실패 - '{}': {}", query.searchText(), e.toString());
            return Optional.empty();
        }
    }

    private Optional<Place> toPlace(JsonNode result, PlaceQuery query) {
        String providerPlaceId = text(result, "place_id");
        JsonNode location = result.path("geometry").path("location");
        Double latitude = number(location, "lat");
        Double longitude = number(location, "lng");
        // 좌표나 식별자가 없는 결과는 저장할 수 없다(Place의 불변식) — 미정규화로 남긴다.
        if (providerPlaceId == null || latitude == null || longitude == null) {
            log.debug("Google Places 결과에 식별자/좌표가 없다 - '{}'", query.searchText());
            return Optional.empty();
        }
        return Optional.of(new Place(
                providerPlaceId,
                // name이 없으면 AI가 준 장소명을 그대로 유지한다.
                text(result, "name") != null ? text(result, "name") : query.placeName(),
                // 분류는 types의 첫 값(가장 구체적인 유형)을 쓴다.
                textList(result, "types").stream().findFirst().orElse(null),
                text(result, "formatted_address"),
                latitude,
                longitude,
                number(result, "rating"),
                List.of(),
                openingHours(providerPlaceId, query)
        ));
    }

    /** Text Search 호출 후 최상위 결과 1건을 반환한다. 결과가 없으면 빈 값. */
    private Optional<JsonNode> searchFirstResult(PlaceQuery query) throws Exception {
        String uri = UriComponentsBuilder.fromUriString(properties.textSearchUrl())
                .queryParam("query", query.searchText())
                .queryParam("language", properties.language())
                .queryParam("key", properties.apiKey())
                .build()
                .toUriString();
        JsonNode root = OBJECT_MAPPER.readTree(
                restClient.get().uri(uri).retrieve().body(String.class));

        String status = text(root, "status");
        if (!"OK".equals(status)) {
            // ZERO_RESULTS는 정상적인 "못 찾음"이고, 그 외(REQUEST_DENIED 등)는 설정 문제다.
            if (!"ZERO_RESULTS".equals(status)) {
                log.warn("Google Places 검색 상태 이상 - '{}': {} {}",
                        query.searchText(), status, text(root, "error_message"));
            }
            return Optional.empty();
        }
        JsonNode results = root.path("results");
        return (results.isArray() && !results.isEmpty())
                ? Optional.of(results.get(0)) : Optional.empty();
    }

    /**
     * Place Details로 운영시간을 보강한다. 실패하거나 운영시간이 없으면 빈 목록 —
     * 부가 정보이므로 장소 조회 자체를 실패시키지 않는다.
     */
    private List<String> openingHours(String providerPlaceId, PlaceQuery query) {
        String uri = UriComponentsBuilder.fromUriString(properties.detailsUrl())
                .queryParam("place_id", providerPlaceId)
                .queryParam("fields", DETAIL_FIELDS)
                .queryParam("language", properties.language())
                .queryParam("key", properties.apiKey())
                .build()
                .toUriString();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(
                    restClient.get().uri(uri).retrieve().body(String.class));
            return textList(root.path("result").path("opening_hours"), "weekday_text");
        } catch (Exception e) {
            log.warn("Google Place Details 조회 실패 - '{}': {}", query.searchText(), e.toString());
            return List.of();
        }
    }
}
