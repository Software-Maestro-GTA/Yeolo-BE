package com.soma.yeolo.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.tasteprofile.client.IntervalRateLimiter;
import com.soma.yeolo.tasteprofile.client.OsmGeocodeProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OpenStreetMap(Nominatim) 기반 Forward Geocode 구현. ({@code geocode.provider=osm}일 때 활성화)
 * AI가 반환한 장소명에 여행 도시·국가 맥락을 붙여 {@code /search}로 조회하고, 첫 결과의 좌표를 내부
 * 장소 정보로 정규화한다. 별도 API 키가 필요 없다.
 *
 * <p>Nominatim 이용 정책상 {@code User-Agent}가 필수이며 초당 1회 이하로 호출해야 하므로,
 * {@link IntervalRateLimiter}로 호출 간 최소 간격을 둔다. 조회 실패·무결과는 예외로 노출하지 않고
 * 좌표 없는 결과({@code placeId}만)로 우아하게 폴백해 코스 생성 파이프라인을 막지 않는다.
 * (docs/architecture.md §5)
 */
public class OsmPlaceNormalizer implements PlaceNormalizer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OsmPlaceNormalizer.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final OsmGeocodeProperties properties;
    private final IntervalRateLimiter rateLimiter;

    public OsmPlaceNormalizer(RestClient restClient, OsmGeocodeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.rateLimiter = new IntervalRateLimiter(properties.minIntervalMs());
    }

    @Override
    public NormalizedPlace normalize(String placeName, String category, String city, String country) {
        Double[] coords = geocode(placeName, city, country);
        return NormalizedPlace.of(country, city, placeName, coords[0], coords[1]);
    }

    /** {@code /search} 호출로 좌표를 얻는다. 어떤 실패든 좌표 {@code [null, null]}로 폴백한다. */
    private Double[] geocode(String placeName, String city, String country) {
        if (placeName == null || placeName.isBlank()) {
            return new Double[]{null, null};
        }
        String uri = UriComponentsBuilder.fromUriString(properties.searchUrl())
                .queryParam("q", query(placeName, city, country))
                .queryParam("format", "jsonv2")
                .queryParam("limit", 1)
                .queryParam("accept-language", properties.language())
                .build()
                .toUriString();
        try {
            rateLimiter.acquire();
            String body = restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.USER_AGENT, properties.userAgent())
                    .retrieve()
                    .body(String.class);
            return parse(body);
        } catch (Exception e) {
            log.warn("Nominatim forward geocode failed for '{}' ({}): {}", placeName, city, e.toString());
            return new Double[]{null, null};
        }
    }

    /** 장소명에 도시·국가 맥락을 콤마로 덧붙여 검색 정확도를 높인다. */
    private String query(String placeName, String city, String country) {
        StringBuilder sb = new StringBuilder(placeName.strip());
        if (city != null && !city.isBlank()) {
            sb.append(", ").append(city.strip());
        }
        if (country != null && !country.isBlank()) {
            sb.append(", ").append(country.strip());
        }
        return sb.toString();
    }

    /** {@code /search} 응답(JSON 배열)의 첫 결과에서 {@code lat}/{@code lon}을 추출한다. */
    private Double[] parse(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            if (root == null || !root.isArray() || root.isEmpty()) {
                return new Double[]{null, null};
            }
            JsonNode first = root.get(0);
            Double lat = toDouble(first.path("lat").asText(null));
            Double lon = toDouble(first.path("lon").asText(null));
            return new Double[]{lat, lon};
        } catch (Exception e) {
            log.warn("Failed to parse Nominatim search response: {}", e.toString());
            return new Double[]{null, null};
        }
    }

    private Double toDouble(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
