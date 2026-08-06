package com.soma.yeolo.place.client;

import static com.soma.yeolo.global.client.JsonNodes.decimalText;
import static com.soma.yeolo.global.client.JsonNodes.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.client.IntervalRateLimiter;
import com.soma.yeolo.global.client.NominatimProperties;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OpenStreetMap(Nominatim) 기반 장소 조회. ({@code place.provider=osm}일 때 활성화)
 *
 * <p>API 키가 필요 없어 개발 환경에서 실제 좌표·주소를 확인하기 좋다. 다만 Nominatim은 평점·사진을
 * 제공하지 않으므로 {@code rating}은 null, {@code photoUrls}는 빈 목록으로 내려간다. 운영시간은
 * {@code extratags.opening_hours}(OSM 원문 표기, 예: {@code Mo-Su 09:00-18:00})가 있을 때만 담는다.
 *
 * <p>공개 서버 정책(≤1req/s)은 역지오코딩과 공유하는 {@code nominatimRateLimiter}가 강제한다.
 * 조회 실패는 포트 계약대로 예외 대신 빈 값으로 돌려준다. (docs/architecture.md §5)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "place.provider", havingValue = "osm")
public class OsmPlaceLookupClient implements PlaceLookupClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final OsmPlaceProperties properties;
    private final String userAgent;
    private final IntervalRateLimiter rateLimiter;

    public OsmPlaceLookupClient(@Qualifier("restClient") RestClient restClient,
                                OsmPlaceProperties properties,
                                NominatimProperties nominatimProperties,
                                IntervalRateLimiter nominatimRateLimiter) {
        this.restClient = restClient;
        this.properties = properties;
        this.userAgent = nominatimProperties.userAgent();
        this.rateLimiter = nominatimRateLimiter;
    }

    @Override
    public Optional<Place> lookup(PlaceQuery query) {
        String uri = UriComponentsBuilder.fromUriString(properties.searchUrl())
                .queryParam("q", query.searchText())
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("extratags", 1)
                .queryParam("limit", 1)
                .queryParam("accept-language", properties.language())
                .build()
                .toUriString();
        try {
            rateLimiter.acquire();
            String body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, userAgent)
                    .retrieve()
                    .body(String.class);
            return parse(body, query);
        } catch (Exception e) {
            log.warn("Nominatim 장소 조회 실패 - '{}': {}", query.searchText(), e.toString());
            return Optional.empty();
        }
    }

    /** 검색 결과 배열의 첫 항목을 장소로 변환한다. 결과가 없거나 식별자·좌표가 없으면 빈 값. */
    private Optional<Place> parse(String body, PlaceQuery query) throws Exception {
        JsonNode results = OBJECT_MAPPER.readTree(body);
        if (!results.isArray() || results.isEmpty()) {
            log.debug("Nominatim 장소 조회 결과 없음 - '{}'", query.searchText());
            return Optional.empty();
        }
        JsonNode result = results.get(0);
        Double latitude = decimalText(result, "lat");
        Double longitude = decimalText(result, "lon");
        String providerPlaceId = providerPlaceId(result);
        // 좌표나 식별자가 없는 결과는 저장할 수 없다(Place의 불변식) — 미정규화로 남긴다.
        if (latitude == null || longitude == null || providerPlaceId == null) {
            return Optional.empty();
        }
        String openingHours = text(result.path("extratags"), "opening_hours");
        return Optional.of(new Place(
                providerPlaceId,
                // name이 없으면 AI가 준 장소명을 그대로 유지한다.
                text(result, "name") != null ? text(result, "name") : query.placeName(),
                category(result),
                text(result, "display_name"),
                latitude,
                longitude,
                null,
                List.of(),
                openingHours == null ? List.of() : List.of(openingHours)
        ));
    }

    /**
     * provider 식별자: OSM은 요소 종류(node/way/relation)와 id의 조합이 유일하다.
     * ({@code osm_type}이 비면 검색 결과 고유 id인 {@code place_id}로 폴백, 둘 다 없으면 null)
     */
    private String providerPlaceId(JsonNode result) {
        String osmType = text(result, "osm_type");
        String osmId = text(result, "osm_id");
        if (osmType != null && osmId != null) {
            return "osm:" + osmType.charAt(0) + osmId;
        }
        String placeId = text(result, "place_id");
        return placeId != null ? "osm:place" + placeId : null;
    }

    /** 분류: Nominatim의 {@code type}(예: attraction) 우선, 없으면 {@code category}(예: tourism). */
    private String category(JsonNode result) {
        String type = text(result, "type");
        String category = type != null ? type : text(result, "category");
        return category != null ? category.toLowerCase(Locale.ROOT) : null;
    }
}
