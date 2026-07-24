package com.soma.yeolo.tasteprofile.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OpenStreetMap(Nominatim) 기반 Reverse Geocode 구현. ({@code geocode.provider=osm}일 때 활성화)
 *
 * <p>Google 구현과 달리 한 번의 {@code /reverse} 호출로 행정구역(country/region/city/district)과
 * 대표 장소명(POI), 장소 유형(category/type)을 모두 얻는다. 별도 API 키가 필요 없어 로컬·개발
 * 환경에서 실제 위치 값을 확인하기 좋다.
 *
 * <p>Nominatim 응답의 {@code address} 블록은 국가별 행정 체계에 따라 키가 달라지므로, 여러 후보
 * 키를 우선순위로 훑어 DOM-5의 country/region/city/district에 매핑한다. 좌표에 결과가 없으면
 * ({@code error} 필드) 빈 위치로 우아하게 폴백하고, HTTP·파싱 실패는
 * {@code REVERSE_GEOCODE_FAILED}로 노출한다. (docs/architecture.md §5)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "geocode.provider", havingValue = "osm")
public class OsmReverseGeocodeClient implements ReverseGeocodeClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 429 응답에 대한 최대 재시도 횟수(레이트리밋으로도 새는 순간적 초과에 대한 안전망). */
    private static final int MAX_RETRIES = 2;
    /** 캐시 키의 좌표 정밀도: 소수 4자리(약 11m) 단위로 뭉쳐 같은 장소의 재조회를 흡수한다. */
    private static final double COORD_SCALE = 10_000.0;

    /** 광역 행정구역(region) 후보 키 — 우선순위 순. */
    private static final List<String> REGION_KEYS = List.of("state", "province", "region");
    /** 도시(city) 후보 키 — 우선순위 순. */
    private static final List<String> CITY_KEYS =
            List.of("city", "town", "municipality", "county", "village");
    /** 세부 행정구역(district) 후보 키 — 우선순위 순. */
    private static final List<String> DISTRICT_KEYS = List.of(
            "city_district", "borough", "district", "suburb", "quarter",
            "neighbourhood", "town", "village", "hamlet");

    private final RestClient restClient;
    private final OsmGeocodeProperties properties;
    private final IntervalRateLimiter rateLimiter;
    /** 좌표(반올림)→위치 결과 LRU 캐시. 동시 접근을 위해 동기화 래핑한다. */
    private final Map<String, GeoLocation> cache;

    public OsmReverseGeocodeClient(@Qualifier("restClient") RestClient restClient,
                                   OsmGeocodeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        this.rateLimiter = new IntervalRateLimiter(properties.minIntervalMs());
        this.cache = newLruCache(properties.cacheMaxSize());
    }

    @Override
    public GeoLocation reverseGeocode(double latitude, double longitude) {
        String key = cacheKey(latitude, longitude);
        GeoLocation cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        GeoLocation location = fetch(latitude, longitude);
        cache.put(key, location);
        return location;
    }

    /** 실제 Nominatim 호출. 호출 전 레이트리밋을 걸고, 429는 백오프 후 제한 횟수만큼 재시도한다. */
    private GeoLocation fetch(double latitude, double longitude) {
        String uri = UriComponentsBuilder.fromUriString(properties.reverseUrl())
                .queryParam("lat", latitude)
                .queryParam("lon", longitude)
                .queryParam("format", "jsonv2")
                .queryParam("zoom", properties.zoom())
                .queryParam("addressdetails", 1)
                .queryParam("accept-language", properties.language())
                .build()
                .toUriString();
        int attempt = 0;
        while (true) {
            rateLimiter.acquire();
            try {
                String body = restClient.get()
                        .uri(uri)
                        .header(HttpHeaders.USER_AGENT, properties.userAgent())
                        .retrieve()
                        .body(String.class);
                return parse(body);
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().value() == HttpStatus.TOO_MANY_REQUESTS.value() && attempt < MAX_RETRIES) {
                    attempt++;
                    long backoffMs = retryAfterMillis(e).orElse(properties.minIntervalMs());
                    log.warn("Nominatim 429 - {}번째 재시도까지 {}ms 대기", attempt, backoffMs);
                    sleep(backoffMs);
                    continue;
                }
                log.error("Nominatim reverse rejected: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
                throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED, e);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.error("Nominatim reverse call failed (connectivity)", e);
                throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED, e);
            }
        }
    }

    /** 좌표를 정밀도 단위로 반올림해 캐시 키를 만든다. 근접 좌표를 같은 키로 뭉쳐 적중률을 높인다. */
    private String cacheKey(double latitude, double longitude) {
        return Math.round(latitude * COORD_SCALE) + "," + Math.round(longitude * COORD_SCALE);
    }

    /** {@code Retry-After}(초 단위)가 있으면 ms로 환산해 백오프에 사용한다. */
    private OptionalLong retryAfterMillis(RestClientResponseException e) {
        String header = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
        if (header != null) {
            try {
                return OptionalLong.of(Long.parseLong(header.trim()) * 1000L);
            } catch (NumberFormatException ignored) {
                // HTTP-date 형식 등은 무시하고 기본 백오프로 폴백한다.
            }
        }
        return OptionalLong.empty();
    }

    private void sleep(long millis) {
        if (millis <= 0L) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED, e);
        }
    }

    private static Map<String, GeoLocation> newLruCache(int maxSize) {
        int capacity = Math.max(1, maxSize);
        return Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, GeoLocation> eldest) {
                return size() > capacity;
            }
        });
    }

    private GeoLocation parse(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            // 좌표에 결과가 없으면 Nominatim은 {"error": ...}를 반환한다 → 빈 위치로 폴백.
            if (root.hasNonNull("error")) {
                log.debug("Nominatim reverse has no result: {}", root.path("error").asText());
                return emptyLocation();
            }
            JsonNode address = root.path("address");

            String country = text(address, "country");
            String region = firstOf(address, REGION_KEYS, null);
            String city = firstOf(address, CITY_KEYS, null);
            // district는 city와 같은 값을 다시 넣지 않는다.
            String district = firstOf(address, DISTRICT_KEYS, city);

            String placeName = placeName(root);
            List<String> placeTypes = placeTypes(root);

            return new GeoLocation(country, city, region, district, placeName, placeTypes);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Nominatim reverse response", e);
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED, e);
        }
    }

    /** 후보 키를 우선순위대로 훑어 첫 유효 값을 반환하되, {@code exclude}와 같은 값은 건너뛴다. */
    private String firstOf(JsonNode address, List<String> keys, String exclude) {
        for (String key : keys) {
            String value = text(address, key);
            if (value != null && !value.equals(exclude)) {
                return value;
            }
        }
        return null;
    }

    /** 대표 장소명: {@code name}이 있으면 사용하고, 없으면 {@code display_name}으로 폴백한다. */
    private String placeName(JsonNode root) {
        String name = text(root, "name");
        return name != null ? name : text(root, "display_name");
    }

    /** 장소 유형: Nominatim의 {@code category}/{@code type}(예: tourism/attraction)을 소문자로 담는다. */
    private List<String> placeTypes(JsonNode root) {
        List<String> types = new ArrayList<>(2);
        addType(types, text(root, "category"));
        addType(types, text(root, "type"));
        return types;
    }

    private void addType(List<String> types, String value) {
        if (value != null) {
            String lower = value.toLowerCase(Locale.ROOT);
            if (!types.contains(lower)) {
                types.add(lower);
            }
        }
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    private GeoLocation emptyLocation() {
        return new GeoLocation(null, null, null, null, null, List.of());
    }
}
