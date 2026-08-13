package com.soma.yeolo.tasteprofile.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Google Maps 기반 Reverse Geocode 구현. ({@code geocode.provider=google}일 때 활성화)
 *
 * <p>두 API를 결합한다:
 * <ul>
 *   <li><b>Geocoding API</b> — 좌표 → 행정구역(country/city/region/district).</li>
 *   <li><b>Places API (New) Nearby Search</b> — 좌표 주변 대표 장소(POI)의 이름·유형
 *       (tourist_attraction, cafe, beach 등)으로 {@code placeName}·{@code placeTypes}를 채운다.
 *       구 Places API는 2025-03 이후 생성된 프로젝트에서 활성화가 불가능해 신 API를 쓴다.</li>
 * </ul>
 *
 * <p>Nearby Search는 성향 분석 품질을 높이는 보강 단계이므로, 실패(권한/일시 오류)해도 분석을
 * 중단하지 않고 Geocoding 결과(formatted_address·행정 타입)로 우아하게 폴백한다. 반면 Geocoding
 * 자체 실패(잘못된 키 등)는 {@code REVERSE_GEOCODE_FAILED}로 노출한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "geocode.provider", havingValue = "google")
public class GoogleReverseGeocodeClient implements ReverseGeocodeClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";

    /** Nearby Search에서 받아올 필드 — 필요한 것만 지정해 호출 비용(티어)을 줄인다. */
    private static final String NEARBY_FIELD_MASK = "places.displayName,places.types";

    private final RestClient restClient;
    private final GoogleGeocodeProperties properties;

    public GoogleReverseGeocodeClient(@Qualifier("restClient") RestClient restClient,
                                      GoogleGeocodeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public GeoLocation reverseGeocode(double latitude, double longitude) {
        AdminAddress admin = geocodeAdmin(latitude, longitude);
        Optional<NearbyPlace> place = nearbyTopPlace(latitude, longitude);

        String placeName = place.map(NearbyPlace::name).orElse(admin.formattedAddress());
        List<String> placeTypes = place.map(NearbyPlace::types).orElse(admin.types());

        return new GeoLocation(admin.country(), admin.city(), admin.region(), admin.district(),
                placeName, placeTypes);
    }

    // ---- Geocoding API (행정구역) --------------------------------------------------

    private AdminAddress geocodeAdmin(double latitude, double longitude) {
        String uri = UriComponentsBuilder.fromUriString(properties.geocodeUrl())
                .queryParam("latlng", latitude + "," + longitude)
                .queryParam("language", properties.language())
                .queryParam("key", properties.apiKey())
                .build()
                .toUriString();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parseAdmin(body);
        } catch (RestClientResponseException e) {
            log.error("Google geocode rejected: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED, e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google geocode call failed (connectivity)", e);
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED, e);
        }
    }

    private AdminAddress parseAdmin(String body) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(body);
            String status = root.path("status").asText();
            if ("ZERO_RESULTS".equals(status)) {
                return AdminAddress.empty();
            }
            if (!"OK".equals(status)) {
                log.error("Google geocode status: {} - {}", status, root.path("error_message").asText(""));
                throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED);
            }
            return toAdminAddress(root.path("results").path(0));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse Google geocode response", e);
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED, e);
        }
    }

    private AdminAddress toAdminAddress(JsonNode result) {
        String country = null;
        String region = null;
        String city = null;
        String district = null;

        for (JsonNode component : result.path("address_components")) {
            List<String> types = textList(component.path("types"));
            String longName = component.path("long_name").asText(null);
            if (types.contains("country")) {
                country = longName;
            } else if (types.contains("administrative_area_level_1")) {
                region = longName;
            } else if (types.contains("locality")) {
                city = longName;
            } else if (city == null && types.contains("administrative_area_level_2")) {
                city = longName;
            }
            if (types.contains("sublocality_level_1") || types.contains("sublocality")) {
                district = longName;
            } else if (district == null && types.contains("administrative_area_level_2")) {
                district = longName;
            }
        }
        return new AdminAddress(country, city, region, district,
                result.path("formatted_address").asText(null), textList(result.path("types")));
    }

    // ---- Places API (New) Nearby Search (POI) --------------------------------------

    /**
     * 좌표 주변 대표 장소를 조회한다. 실패·결과 없음이면 {@code Optional.empty()}로 폴백한다.
     *
     * <p>FieldMask로 이름·유형만 요청한다 — 신 API는 요청 필드에 따라 과금 티어가 갈린다.
     * 순위는 기본값인 인기도(POPULARITY)를 그대로 쓴다(구 API의 prominence와 같은 의미).
     *
     * <p><b>URL이 비어 있으면 호출 자체를 건너뛴다.</b> Nearby는 Geocoding과 <b>다른 API</b>
     * (Places API (New))라 키에 Geocoding만 열려 있으면 매번 403이 된다. 이 보강은 실패해도 폴백이
     * 있으니 기능은 돌지만, 사진 <b>한 장마다</b> 실패가 확정된 왕복이 붙는다 — 그 구간이 SSE 응답
     * 지연에 그대로 들어가므로 안 켤 거면 아예 부르지 않는다.
     */
    private Optional<NearbyPlace> nearbyTopPlace(double latitude, double longitude) {
        if (properties.searchNearbyUrl() == null || properties.searchNearbyUrl().isBlank()) {
            return Optional.empty();
        }
        try {
            String body = restClient.post()
                    .uri(properties.searchNearbyUrl())
                    .header(API_KEY_HEADER, properties.apiKey())
                    .header(FIELD_MASK_HEADER, NEARBY_FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(nearbyRequestBody(latitude, longitude))
                    .retrieve()
                    .body(String.class);
            return parseNearby(body);
        } catch (Exception e) {
            // 보강 단계 실패는 분석을 막지 않는다 — Geocoding 폴백 사용.
            log.warn("Places Nearby Search failed, falling back to geocoding place info: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String nearbyRequestBody(double latitude, double longitude) throws Exception {
        ObjectNode center = OBJECT_MAPPER.createObjectNode()
                .put("latitude", latitude)
                .put("longitude", longitude);
        ObjectNode circle = OBJECT_MAPPER.createObjectNode()
                .put("radius", properties.nearbyRadius());
        circle.set("center", center);
        ObjectNode locationRestriction = OBJECT_MAPPER.createObjectNode();
        locationRestriction.set("circle", circle);

        ObjectNode request = OBJECT_MAPPER.createObjectNode()
                .put("maxResultCount", 1);
        if (properties.language() != null && !properties.language().isBlank()) {
            request.put("languageCode", properties.language());
        }
        request.set("locationRestriction", locationRestriction);
        return OBJECT_MAPPER.writeValueAsString(request);
    }

    private Optional<NearbyPlace> parseNearby(String body) {
        try {
            // 결과가 없으면 신 API는 에러가 아니라 빈 객체({})를 준다.
            JsonNode top = OBJECT_MAPPER.readTree(body).path("places").path(0);
            String name = top.path("displayName").path("text").asText(null);
            if (name == null) {
                return Optional.empty();
            }
            return Optional.of(new NearbyPlace(name, textList(top.path("types"))));
        } catch (Exception e) {
            log.warn("Failed to parse Places Nearby Search response: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ---- helpers -------------------------------------------------------------------

    private List<String> textList(JsonNode array) {
        if (array == null || !array.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>(array.size());
        array.forEach(node -> values.add(node.asText().toLowerCase(Locale.ROOT)));
        return values;
    }

    /** Geocoding으로 얻은 행정구역 정보 + POI 폴백용 값. */
    private record AdminAddress(
            String country,
            String city,
            String region,
            String district,
            String formattedAddress,
            List<String> types
    ) {
        static AdminAddress empty() {
            return new AdminAddress(null, null, null, null, null, List.of());
        }
    }

    /** Nearby Search로 얻은 대표 장소. */
    private record NearbyPlace(String name, List<String> types) {
    }
}
