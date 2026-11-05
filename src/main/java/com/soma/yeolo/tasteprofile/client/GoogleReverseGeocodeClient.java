package com.soma.yeolo.tasteprofile.client;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.soma.yeolo.global.client.LruCache;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 *
 * <p><b>좌표 격자 캐시.</b> 사진 한 장마다 Geocoding과 Nearby를 한 번씩 부르는 구조인데, 여행 사진은
 * 같은 장소에 몰려서 좌표가 몇 m씩만 다른 요청이 수십 번 반복된다(2026-11 prod 기준 요청당 사진 수십 장,
 * 하루 수만 호출 — Nearby Search는 Pro가 최저 티어라 $32/1k로 비용의 중심이었다). 그래서 좌표를
 * {@code cacheGridDecimals} 자리로 반올림한 <b>격자 칸</b>을 키로 LRU 캐시한다.
 *
 * <p><b>Google에는 칸 좌표가 아니라 그 칸에 먼저 온 사진의 원 좌표로 묻는다(first-photo-wins).</b> 반경도
 * 설정값 그대로다. 그래서 칸의 답은 "실제 사진 한 장이 캐시 전에 받았을 답"과 정확히 같고, 같은 칸의 다른
 * 사진은 그 답을 물려받는다 — 결과 집합이 기존 답의 부분 표본이지 다른 종류의 답이 아니다. 칸 좌표로
 * 묻고 반경을 칸 대각선만큼 넓히는 방식은 어느 사진의 반경 안에도 없던 더 유명한 장소가 인기도 순위로
 * 뽑히는 편향이 생겨 택하지 않았다. 남는 근사는 "같은 칸(3자리 ≈ 111m×90m) 안에서 100m 넘게 떨어진 서로
 * 다른 장소를 찍으면 한쪽으로 합쳐진다"뿐이고, 그때도 결과는 실제 찍은 두 곳 중 하나다. 행정구역은 칸
 * 경계에서 이웃 동(洞)으로 갈릴 수 있는 정도다. 대신 어느 사진이 먼저 오느냐에 따라 칸의 답이 달라질 수
 * 있는데, 분석 품질과는 무관하다.
 *
 * <p><b>무엇을 캐시하나.</b> 칸마다 <b>행정구역(Geocoding)</b>과 <b>완성된 위치(Geocoding+Nearby)</b>를
 * 따로 든다. Nearby가 <b>일시 실패</b>(HTTP 오류·깨진 응답)하면 완성 위치는 캐시하지 않되 — 한 번의
 * 429/5xx가 그 칸을 주소명으로 굳히지 않게 — 이미 성공한 행정구역은 남겨 다음 사진은 Nearby만 다시 묻는다.
 * Nearby "결과 없음"은 정상 응답이므로 캐시한다. Geocoding 실패(예외)는 아무것도 캐시하지 않는다.
 * Geocoding이 전처리가 받아 줄 행정구역을 못 채우면({@code ZERO_RESULTS}, 나라·plus_code만 오는 연안 결과 —
 * 판정은 전처리 필터 {@link GeoLocation#isAnalyzable}와 같은 기준) 어차피 걸러질 사진이라 Nearby(Pro 티어)를
 * 부르지 않고 캐시도 하지 않는다 — 빈 위치를 캐시하면 그 칸의 뭍 사진까지 전부 분석에서 빠진다.
 * 저장은 "완성 항목을 부분 항목으로 덮지 않는" merge라 동시 요청이 겹쳐도 캐시가 뒤로 가지 않는다. 다만 같은
 * 칸을 <b>동시에</b> 처음 조회하는 요청들은 각자 Google을 부른다(진행 중 요청을 합치는 장치는 없다) — 절감은
 * 같은 요청 안의 사진 묶음과 시간차가 있는 요청 사이에서 나며, 동시성 중복은 best-effort로 둔다.
 *
 * <p>좌표는 {@link BigDecimal} plain 표기로 보낸다 — {@code Double.toString}은 1e-3 아래에서 지수 표기
 * ({@code 1.0E-4})로 바뀌어 Geocoding이 {@code INVALID_REQUEST}로 거부하고(적도·본초자오선 부근), Jackson은
 * {@code 40.0} 같은 BigDecimal의 뒤 0을 떼어 {@code 4E+1}로 쓴다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "geocode.provider", havingValue = "google")
public class GoogleReverseGeocodeClient implements ReverseGeocodeClient {

    /** 좌표(BigDecimal)를 지수 표기 없이 쓴다 — Google은 {@code 1E-7} 같은 숫자를 거부한다. */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

    /** Places API (New) Nearby Search가 허용하는 최대 반경(m). */
    private static final int NEARBY_MAX_RADIUS = 50_000;

    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";

    /** Nearby Search에서 받아올 필드 — 필요한 것만 지정해 호출 비용(티어)을 줄인다. */
    private static final String NEARBY_FIELD_MASK = "places.displayName,places.types";

    private final RestClient restClient;
    private final GoogleGeocodeProperties properties;
    /** 격자 칸의 좌표 소수 자릿수. */
    private final int gridDecimals;
    /** 격자 칸("위도,경도")→캐시 항목 LRU. 동시 접근을 위해 동기화 래핑되어 있다. */
    private final Map<String, CellEntry> cache;

    public GoogleReverseGeocodeClient(@Qualifier("restClient") RestClient restClient,
                                      GoogleGeocodeProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
        int decimals = properties.cacheGridDecimals();
        // 3자리 미만이면 칸이 1km 이상이라 칸 안 사진이 서로 다른 장소를 찍은 것이 되어 "한 칸 = 한 답" 근사가
        // 깨지고, 8자리 이상이면 1cm 아래라 캐시가 적중할 일이 없다.
        if (decimals < 3 || decimals > 7) {
            throw new IllegalArgumentException(
                    "geocode.google.cache-grid-decimals must be between 3 and 7, but was " + decimals);
        }

        this.gridDecimals = decimals;
        if (nearbyEnabled()) {
            // Integer라 비어 있을 수 있다 — 요청 본문에서 NPE로 죽는 대신 원인을 말하고 기동을 막는다.
            // Nearby가 꺼져 있으면(URL 비움) 반경은 읽지 않으므로 검증도 하지 않는다. 상한을 넘기면 Nearby가
            // 전부 400이라 조용히 주소명으로 굳는다.
            Integer nearbyRadius = properties.nearbyRadius();
            if (nearbyRadius == null || nearbyRadius <= 0 || nearbyRadius > NEARBY_MAX_RADIUS) {
                throw new IllegalArgumentException("geocode.google.nearby-radius must be 1.."
                        + NEARBY_MAX_RADIUS + " meters, but was " + nearbyRadius);
            }
        }
        this.cache = LruCache.newSynchronized(properties.cacheMaxSize(), "geocode.google.cache-max-size");
    }

    @Override
    public GeoLocation reverseGeocode(double latitude, double longitude) {
        String cell = snapToGrid(latitude).toPlainString() + "," + snapToGrid(longitude).toPlainString();

        CellEntry entry = cache.get(cell);
        if (entry != null && entry.location() != null) {
            log.debug("Google reverse geocode 캐시 적중 - cell={}", cell);
            return entry.location();
        }
        log.debug("Google reverse geocode 캐시 미적중 - cell={}", cell);

        if (entry == null) {
            // 이 사진의 원 좌표가 칸의 대표점이 된다(first-photo-wins).
            BigDecimal queryLatitude = BigDecimal.valueOf(latitude);
            BigDecimal queryLongitude = BigDecimal.valueOf(longitude);
            AdminAddress admin = geocodeAdmin(queryLatitude, queryLongitude);
            if (!admin.isAnalyzable()) {
                // 전처리에서 걸러질 위치다 — Nearby(Pro)를 사지 않고, 캐시도 하지 않는다(같은 칸의 다음 사진은 뭍일 수 있다).
                return toLocation(admin, Optional.empty());
            }
            entry = store(cell, new CellEntry(admin, queryLatitude, queryLongitude, null));
            if (entry.location() != null) {
                return entry.location();   // 같은 칸을 동시에 조회한 다른 스레드가 먼저 완성했다
            }
        }

        NearbyResult nearby = nearbyTopPlace(entry.queryLatitude(), entry.queryLongitude());
        GeoLocation location = toLocation(entry.admin(), nearby.place());
        // 장소명까지 채워진 결과만 완성으로 캐시한다 — formatted_address도 없고 Nearby도 비면 전처리에서 걸러질
        // 위치인데, 그걸 칸에 고정하면 그 칸의 사진이 전부 분석에서 빠진다. 행정구역 항목은 남겨 Nearby만 재시도한다.
        if (!nearby.failed() && location.isAnalyzable()) {
            store(cell, entry.completed(location));
        }
        return location;
    }

    /**
     * 캐시에 넣되 <b>완성 항목을 부분 항목으로 덮지 않는다.</b> 같은 칸을 동시에 조회한 다른 스레드가 먼저
     * 완성해 두었으면 그쪽을 쓴다. {@code Collections.synchronizedMap}의 {@code merge}는 원자적이다.
     */
    private CellEntry store(String cell, CellEntry entry) {
        return cache.merge(cell, entry, (existing, fresh) -> existing.location() != null ? existing : fresh);
    }

    private boolean nearbyEnabled() {
        return properties.searchNearbyUrl() != null && !properties.searchNearbyUrl().isBlank();
    }

    private static GeoLocation toLocation(AdminAddress admin, Optional<NearbyPlace> place) {
        String placeName = place.map(NearbyPlace::name).orElse(admin.formattedAddress());
        List<String> placeTypes = place.map(NearbyPlace::types).orElse(admin.types());
        return new GeoLocation(admin.country(), admin.city(), admin.region(), admin.district(),
                placeName, placeTypes);
    }

    /** 좌표를 격자 칸의 좌표로 반올림한다(3자리면 33.45812 → 33.458). HALF_UP이라 음수도 절댓값 기준으로 대칭이다. */
    private BigDecimal snapToGrid(double coordinate) {
        return BigDecimal.valueOf(coordinate).setScale(gridDecimals, RoundingMode.HALF_UP);
    }

    // ---- Geocoding API (행정구역) --------------------------------------------------

    private AdminAddress geocodeAdmin(BigDecimal latitude, BigDecimal longitude) {
        // 실제 Google 호출은 이 한 줄로 센다 — 취향 분석 요청당 사진 수(ImageMetadataPreprocessor 로그)와
        // 대조하면 캐시 효과(호출 절감률)를 운영 로그만으로 확인할 수 있다. 좌표는 사용자 위치라 INFO에 남기지 않는다.
        log.info("Google Geocoding 호출");
        String uri = UriComponentsBuilder.fromUriString(properties.geocodeUrl())
                .queryParam("latlng", latitude.toPlainString() + "," + longitude.toPlainString())
                .queryParam("language", properties.language())
                .queryParam("key", properties.apiKey())
                .build()
                .toUriString();
        try {
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            return parseAdmin(body);
        } catch (RestClientResponseException e) {
            // Spring의 예외 메시지에는 요청 URI(= API 키)가 실린다. 로그에 남기지 않을뿐더러 BusinessException의
            // cause로도 붙이지 않는다 — 전역 예외 핸들러가 5xx의 cause 체인을 통째로 로그에 쓰기 때문이다.
            log.error("Google geocode rejected: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Google geocode call failed (connectivity): {}", describeRootCause(e));
            throw new BusinessException(ErrorCode.REVERSE_GEOCODE_FAILED);
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

    /**
     * address_components를 DOM-4 §1의 국가/도시/지역/행정구역으로 매핑한다.
     *
     * <p><b>행정 단계를 먼저 모아 두고 폴백 체인으로 고른다.</b> 국가·지역별로 존재하는 단계가
     * 달라서, 특정 타입 하나에 필드를 고정하면 그 타입이 없는 지역에서 통째로 {@code null}이 되기
     * 때문이다. 실제로 <b>서울·부산 등 특별시·광역시는 {@code locality}도
     * {@code administrative_area_level_2}도 내려오지 않는다</b>(서울특별시 =
     * {@code administrative_area_level_1}, 마포구 = {@code sublocality_level_1}). 이 둘만 보던
     * 이전 매핑에서는 서울 좌표의 {@code city}가 전부 비었고, AI({@code LocationSchema})가
     * {@code city}를 non-nullable로 받으므로 <b>사진 한 장만 서울이어도 요청 전체가 400</b>
     * ("분석 가능한 전처리 메타데이터가 부족합니다.")으로 반려됐다.
     *
     * <p>단계 선택은 OSM 구현({@link OsmReverseGeocodeClient})과 같은 의미론을 따른다 —
     * region = 시·도, city = 시·군·구, district = 읍·면·동.
     */
    private AdminAddress toAdminAddress(JsonNode result) {
        String country = null;
        String region = null;          // administrative_area_level_1: 서울특별시, 경상북도
        String adminLevel2 = null;     // administrative_area_level_2: 서귀포시
        String locality = null;        // locality: 칠곡군, 大阪市, San Francisco
        String sublocalityL1 = null;   // sublocality_level_1: 마포구, 서초구
        String sublocalityL2 = null;   // sublocality_level_2: 공덕동, 왜관읍
        String sublocality = null;     // 레벨 없는 sublocality: 남부순환로, 군청1길 (도로명)

        for (JsonNode component : result.path("address_components")) {
            List<String> types = textList(component.path("types"));
            String longName = text(component.path("long_name"));
            // 한 컴포넌트가 여러 타입을 갖는다(세종특별자치시 = locality + administrative_area_level_1,
            // 마포구 = sublocality + sublocality_level_1). 넓은 단계부터 판정해 광역 행정구역이
            // 아래 단계로 내려앉지 않게 하고, 레벨이 붙은 sublocality를 레벨 없는 쪽보다 먼저 잡는다.
            if (types.contains("country")) {
                country = longName;
            } else if (types.contains("administrative_area_level_1")) {
                region = longName;
            } else if (types.contains("administrative_area_level_2")) {
                adminLevel2 = longName;
            } else if (types.contains("locality")) {
                locality = longName;
            } else if (types.contains("sublocality_level_1")) {
                sublocalityL1 = longName;
            } else if (types.contains("sublocality_level_2")) {
                sublocalityL2 = longName;
            } else if (types.contains("sublocality")) {
                sublocality = longName;
            }
        }

        // 레벨 없는 sublocality는 도로명이 오기도 하므로 행정구역 후보 중 가장 뒤에 둔다.
        String city = firstNonBlank(locality, adminLevel2, sublocalityL1, region);
        String district = firstNonBlank(sublocalityL2, sublocalityL1, sublocality, adminLevel2, city);

        return new AdminAddress(country, city, region, district,
                text(result.path("formatted_address")), textList(result.path("types")));
    }

    // ---- Places API (New) Nearby Search (POI) --------------------------------------

    /**
     * 좌표 주변 대표 장소를 조회한다. 결과 없음은 {@code NearbyResult.of(Optional.empty())}(정상, 캐시 대상), 호출·파싱
     * 실패는 {@code NearbyResult.callFailed()}(일시적일 수 있어 캐시 제외)로 돌려주며 둘 다 장소는 비어 있다.
     *
     * <p>FieldMask로 이름·유형만 요청한다 — 신 API는 요청 필드에 따라 과금 티어가 갈린다.
     * 순위는 기본값인 인기도(POPULARITY)를 그대로 쓴다(구 API의 prominence와 같은 의미).
     *
     * <p><b>URL이 비어 있으면 호출 자체를 건너뛴다.</b> Nearby는 Geocoding과 <b>다른 API</b>
     * (Places API (New))라 키에 Geocoding만 열려 있으면 매번 403이 된다. 이 보강은 실패해도 폴백이
     * 있으니 기능은 돌지만, 사진 <b>한 장마다</b> 실패가 확정된 왕복이 붙는다 — 그 구간이 SSE 응답
     * 지연에 그대로 들어가므로 안 켤 거면 아예 부르지 않는다.
     */
    private NearbyResult nearbyTopPlace(BigDecimal latitude, BigDecimal longitude) {
        if (!nearbyEnabled()) {
            return NearbyResult.of(Optional.empty());
        }
        log.info("Google Nearby Search 호출");   // 비용의 중심(Pro 티어) — Geocoding과 따로 센다
        try {
            String body = restClient.post()
                    .uri(properties.searchNearbyUrl())
                    .header(API_KEY_HEADER, properties.apiKey())
                    .header(FIELD_MASK_HEADER, NEARBY_FIELD_MASK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(nearbyRequestBody(latitude, longitude))
                    .retrieve()
                    .body(String.class);
            return NearbyResult.of(parseNearby(body));
        } catch (Exception e) {
            // 보강 단계 실패는 분석을 막지 않는다 — Geocoding 폴백 사용. 단, 일시 실패일 수 있으니 캐시는 안 한다.
            log.warn("Places Nearby Search failed, falling back to geocoding place info: {}", e.getMessage());
            return NearbyResult.callFailed();
        }
    }

    private String nearbyRequestBody(BigDecimal latitude, BigDecimal longitude) throws Exception {
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

    /** 깨진 본문은 예외를 그대로 올려 호출부에서 "일시 실패"로 분류되게 한다(캐시 제외). */
    private Optional<NearbyPlace> parseNearby(String body) throws Exception {
        // 결과가 없으면 신 API는 에러가 아니라 빈 객체({})를 준다.
        JsonNode top = OBJECT_MAPPER.readTree(body).path("places").path(0);
        String name = text(top.path("displayName").path("text"));
        if (name == null) {
            return Optional.empty();   // 비어 있는 이름은 결과 없음과 같다(장소명으로 쓸 수 없다)
        }
        return Optional.of(new NearbyPlace(name, textList(top.path("types"))));
    }

    // ---- helpers -------------------------------------------------------------------

    /** 연결 실패의 근본 원인(클래스·메시지)만 — Spring의 래핑 예외 메시지는 키가 든 요청 URI를 포함한다. */
    private static String describeRootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    /** 노드의 텍스트를 읽되 빈 문자열은 {@code null}로 취급한다(폴백 체인이 빈 값을 고르지 않도록). */
    private String text(JsonNode node) {
        String value = node.asText(null);
        return (value == null || value.isBlank()) ? null : value;
    }

    /** 폴백 체인 — 앞에서부터 비어 있지 않은 첫 값을 고른다. 전부 비면 {@code null}. */
    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

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

        /**
         * 전처리가 분석 가능으로 받아 줄 행정구역이 다 있는가 — {@link GeoLocation#hasAdminArea}와 같은 술어다
         * (장소명은 Nearby 또는 formatted_address로 채운다). {@code ZERO_RESULTS}(바다 등)는 물론,
         * 나라·plus_code만 오는 연안 결과도 거짓이다.
         */
        boolean isAnalyzable() {
            return GeoLocation.hasAdminArea(country, city, region, district);
        }
    }

    /** Nearby Search로 얻은 대표 장소. */
    private record NearbyPlace(String name, List<String> types) {
    }

    /**
     * 격자 칸 하나의 캐시 항목. {@code admin}은 Geocoding 결과, {@code queryLatitude/queryLongitude}는 이 칸의
     * 대표점(먼저 온 사진의 원 좌표), {@code location}은 Nearby까지 끝난 완성 위치이며 아직 못 끝냈으면(일시 실패)
     * {@code null}이다.
     */
    private record CellEntry(AdminAddress admin, BigDecimal queryLatitude, BigDecimal queryLongitude,
                             GeoLocation location) {
        CellEntry completed(GeoLocation location) {
            return new CellEntry(admin, queryLatitude, queryLongitude, location);
        }
    }

    /** Nearby 조회 결과 — "결과 없음"(정상, 캐시함)과 "호출 실패"(일시적일 수 있음, 캐시 안 함)를 구분한다. */
    private record NearbyResult(Optional<NearbyPlace> place, boolean failed) {
        static NearbyResult of(Optional<NearbyPlace> place) {
            return new NearbyResult(place, false);
        }

        static NearbyResult callFailed() {
            return new NearbyResult(Optional.empty(), true);
        }
    }
}
