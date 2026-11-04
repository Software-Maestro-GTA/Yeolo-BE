package com.soma.yeolo.tasteprofile.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleReverseGeocodeClientTest {

    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String NEARBY_URL = "https://places.googleapis.com/v1/places:searchNearby";

    private static final String GEOCODE_OK = """
            {
              "results": [{
                "address_components": [
                  {"long_name": "성산읍", "types": ["sublocality_level_1","sublocality","political"]},
                  {"long_name": "서귀포시", "types": ["administrative_area_level_2","political"]},
                  {"long_name": "제주특별자치도", "types": ["administrative_area_level_1","political"]},
                  {"long_name": "대한민국", "types": ["country","political"]}
                ],
                "formatted_address": "대한민국 제주특별자치도 서귀포시 성산읍",
                "types": ["political"]
              }],
              "status": "OK"
            }
            """;

    /**
     * 특별시·광역시 응답 — {@code locality}도 {@code administrative_area_level_2}도 없다.
     * 이 두 타입만 보던 매핑에서는 {@code city}가 통째로 비었고, AI가 그 필드를 non-nullable로
     * 받으므로 서울 사진 한 장이 요청 전체를 400으로 만들었다.
     */
    private static final String GEOCODE_SEOUL = """
            {
              "results": [{
                "address_components": [
                  {"long_name": "공덕동", "types": ["sublocality","sublocality_level_2","political"]},
                  {"long_name": "마포구", "types": ["sublocality","sublocality_level_1","political"]},
                  {"long_name": "서울특별시", "types": ["administrative_area_level_1","political"]},
                  {"long_name": "대한민국", "types": ["country","political"]}
                ],
                "formatted_address": "대한민국 서울특별시 마포구 공덕동",
                "types": ["political"]
              }],
              "status": "OK"
            }
            """;

    private RestClient.Builder clientBuilder;
    private MockRestServiceServer server;
    private GoogleReverseGeocodeClient client;

    @BeforeEach
    void setUp() {
        clientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(clientBuilder).build();
        client = new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, NEARBY_URL, 100, "ko", 3, 4096));
    }

    @Test
    void 행정구역은_geocoding_장소명유형은_nearby로_채운다() {
        String nearby = """
                {
                  "places": [{
                    "displayName": {"text": "성산일출봉", "languageCode": "ko"},
                    "types": ["tourist_attraction","point_of_interest","establishment"]
                  }]
                }
                """;
        server.expect(requestTo(containsString("/geocode/json")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        // 신 API는 POST + 헤더 키 + FieldMask, 반경은 body의 locationRestriction으로 간다.
        server.expect(requestTo(NEARBY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", "places.displayName,places.types"))
                .andExpect(jsonPath("$.locationRestriction.circle.center.latitude").value(33.45))
                .andExpect(jsonPath("$.locationRestriction.circle.radius").value(100))
                .andRespond(withSuccess(nearby, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        // 행정구역 — Geocoding
        assertThat(location.country()).isEqualTo("대한민국");
        assertThat(location.region()).isEqualTo("제주특별자치도");
        assertThat(location.city()).isEqualTo("서귀포시");
        assertThat(location.district()).isEqualTo("성산읍");
        // 장소명·유형 — Nearby Search
        assertThat(location.placeName()).isEqualTo("성산일출봉");
        assertThat(location.placeTypes()).containsExactly("tourist_attraction", "point_of_interest", "establishment");
        server.verify();
    }

    @Test
    void nearby가_실패하면_geocoding_값으로_폴백한다() {
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withServerError());

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        assertThat(location.city()).isEqualTo("서귀포시");
        assertThat(location.placeName()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        assertThat(location.placeTypes()).containsExactly("political");
        server.verify();
    }

    @Test
    void nearby가_결과없음이면_geocoding_값으로_폴백한다() {
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        assertThat(location.placeName()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        assertThat(location.placeTypes()).containsExactly("political");
    }

    /**
     * Nearby는 Geocoding과 다른 API(Places API (New))라 키에 열려 있지 않으면 매번 403이다.
     * 안 쓸 거면 사진마다 실패가 확정된 왕복을 붙이지 않는다 — 그 지연이 SSE 응답에 그대로 들어간다.
     */
    @Test
    void nearby_url이_비어_있으면_호출하지_않고_geocoding_값만_쓴다() {
        client = new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", 100, "ko", 3, 4096));
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        assertThat(location.city()).isEqualTo("서귀포시");
        assertThat(location.placeName()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        // Nearby 요청이 하나도 나가지 않았음을 확인한다(expect한 geocode 1건만 소비).
        server.verify();
    }

    /**
     * 회귀 방지 — 기존 픽스처(제주/서귀포시)는 {@code administrative_area_level_2}가 있어서 이
     * 구멍을 못 잡았다. 서울은 그 타입이 없으므로 {@code sublocality_level_1}까지 폴백해야 한다.
     */
    @Test
    void 특별시는_locality와_admin2가_없어도_city를_채운다() {
        client = new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", 100, "ko", 3, 4096));
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_SEOUL, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(37.5445, 126.9509);

        assertThat(location.country()).isEqualTo("대한민국");
        assertThat(location.region()).isEqualTo("서울특별시");
        assertThat(location.city()).isEqualTo("마포구");
        assertThat(location.district()).isEqualTo("공덕동");
        assertThat(location.isAnalyzable()).isTrue();
        server.verify();
    }

    /**
     * 실제 응답에는 레벨 없는 {@code sublocality}로 <b>도로명</b>이 섞여 온다(서초구 좌표의
     * "남부순환로"). 레벨이 붙은 {@code sublocality_level_1}(서초구)을 먼저 잡아야 도로명이
     * 행정구역 자리에 들어가지 않는다.
     */
    @Test
    void 레벨없는_sublocality_도로명은_행정구역으로_쓰지_않는다() {
        String withRoadName = """
                {
                  "results": [{
                    "address_components": [
                      {"long_name": "남부순환로", "types": ["sublocality","political"]},
                      {"long_name": "서초구", "types": ["sublocality","sublocality_level_1","political"]},
                      {"long_name": "서울특별시", "types": ["administrative_area_level_1","political"]},
                      {"long_name": "대한민국", "types": ["country","political"]}
                    ],
                    "formatted_address": "대한민국 서울특별시 서초구 남부순환로",
                    "types": ["street_address"]
                  }],
                  "status": "OK"
                }
                """;
        client = new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", 100, "ko", 3, 4096));
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(withRoadName, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(37.4839, 127.0330);

        assertThat(location.city()).isEqualTo("서초구");
        assertThat(location.district()).isEqualTo("서초구");
        assertThat(location.isAnalyzable()).isTrue();
    }

    /** 구·동까지 안 내려오는 얕은 응답이어도 region으로 폴백해 city·district를 비우지 않는다. */
    @Test
    void 광역행정구역만_내려와도_city와_district가_비지_않는다() {
        String shallow = """
                {
                  "results": [{
                    "address_components": [
                      {"long_name": "서울특별시", "types": ["administrative_area_level_1","political"]},
                      {"long_name": "대한민국", "types": ["country","political"]}
                    ],
                    "formatted_address": "대한민국 서울특별시",
                    "types": ["political"]
                  }],
                  "status": "OK"
                }
                """;
        client = new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", 100, "ko", 3, 4096));
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(shallow, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(37.5665, 126.9780);

        assertThat(location.city()).isEqualTo("서울특별시");
        assertThat(location.district()).isEqualTo("서울특별시");
        assertThat(location.isAnalyzable()).isTrue();
    }

    /** 나라조차 없는 위치는 전처리에서 걸러지므로 Nearby(Pro 티어)를 부르지 않는다. */
    @Test
    void geocoding이_결과없음이면_nearby_없이_미상_위치를_반환한다() {
        server.expect(ExpectedCount.once(), requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess("{\"results\":[],\"status\":\"ZERO_RESULTS\"}",
                        MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(0.0, 0.0);

        assertThat(location.country()).isNull();
        assertThat(location.placeName()).isNull();
        assertThat(location.placeTypes()).isEmpty();
        server.verify();
    }

    // ---- 좌표 격자 캐시 ----------------------------------------------------------------

    private static final String NEARBY_SEONGSAN = """
            {"places": [{"displayName": {"text": "성산일출봉"}, "types": ["tourist_attraction"]}]}
            """;

    /**
     * 사진 한 장마다 Google을 두 번 부르던 비용의 핵심 절감 지점. 같은 칸(소수 3자리, 약 100m)의 좌표는
     * 먼저 온 사진의 원 좌표로 한 번만 조회하고(반경은 설정값 그대로), 나머지는 그 답을 물려받는다.
     */
    @Test
    void 같은_격자_칸의_좌표는_첫_사진의_원_좌표로_한_번만_조회하고_재사용한다() {
        server.expect(ExpectedCount.once(), requestTo(containsString("latlng=33.45812,126.94253")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(NEARBY_URL))
                .andExpect(jsonPath("$.locationRestriction.circle.center.latitude").value(33.45812))
                .andExpect(jsonPath("$.locationRestriction.circle.center.longitude").value(126.94253))
                .andExpect(jsonPath("$.locationRestriction.circle.radius").value(100))
                .andRespond(withSuccess(NEARBY_SEONGSAN, MediaType.APPLICATION_JSON));

        GeoLocation first = client.reverseGeocode(33.45812, 126.94253);
        GeoLocation second = client.reverseGeocode(33.45834, 126.94271);   // 같은 칸 33.458,126.943

        assertThat(first.placeName()).isEqualTo("성산일출봉");
        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void 다른_격자_칸이면_다시_조회한다() {
        server.expect(ExpectedCount.times(2), requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.times(2), requestTo(NEARBY_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.reverseGeocode(33.45812, 126.94253);   // 33.458,126.943
        client.reverseGeocode(33.45871, 126.94198);   // 33.459,126.942

        server.verify();
    }

    /** 남반구·서경은 음수다. HALF_UP 반올림이라 절댓값 기준으로 대칭이다 — 같은 칸 판정을 고정해 둔다. */
    @Test
    void 음수_좌표도_같은_규칙으로_칸을_나눈다() {
        server.expect(ExpectedCount.once(), requestTo(containsString("latlng=-33.8688,151.2093")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(NEARBY_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.reverseGeocode(-33.8688, 151.2093);   // -33.869,151.209
        client.reverseGeocode(-33.8691, 151.2087);   // -33.869,151.209

        server.verify();
    }

    /**
     * 해안 사진은 Geocoding이 ZERO_RESULTS를 줄 수 있다. 그 빈 결과를 칸에 캐시하면 같은 칸의 뭍 사진까지
     * 전부 분석에서 빠지므로 캐시하지 않고, Nearby도 부르지 않는다(어차피 걸러질 사진에 Pro 호출을 쓰지 않는다).
     */
    @Test
    void geocoding이_결과없음이면_캐시하지_않고_같은_칸의_다음_사진은_다시_묻는다() {
        String zero = "{\"results\":[],\"status\":\"ZERO_RESULTS\"}";
        server.expect(requestTo(containsString("latlng=33.45812,126.94253")))
                .andRespond(withSuccess(zero, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("latlng=33.45834,126.94271")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withSuccess(NEARBY_SEONGSAN, MediaType.APPLICATION_JSON));

        GeoLocation atSea = client.reverseGeocode(33.45812, 126.94253);
        GeoLocation ashore = client.reverseGeocode(33.45834, 126.94271);

        assertThat(atSea.isAnalyzable()).isFalse();
        assertThat(ashore.placeName()).isEqualTo("성산일출봉");
        server.verify();
    }

    /**
     * 연안에서는 Geocoding이 OK이면서 나라·plus_code만 주기도 한다. 나라만 보고 "뭍"으로 판정해 캐시하면 그 칸의
     * 뭍 사진까지 전부 분석에서 빠진다 — 전처리 필터와 같은 기준(시·도·구까지)으로 판정해 캐시하지 않는다.
     */
    @Test
    void 나라만_오는_반쪽_결과는_캐시하지_않고_nearby도_부르지_않는다() {
        String countryOnly = """
                {"status":"OK","results":[{
                  "formatted_address":"XR4R+2W 대한민국",
                  "address_components":[{"long_name":"대한민국","types":["country","political"]}],
                  "types":["plus_code"]
                }]}
                """;
        server.expect(requestTo(containsString("latlng=33.45812,126.94253")))
                .andRespond(withSuccess(countryOnly, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("latlng=33.45834,126.94271")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(NEARBY_URL))
                .andRespond(withSuccess(NEARBY_SEONGSAN, MediaType.APPLICATION_JSON));

        GeoLocation partial = client.reverseGeocode(33.45812, 126.94253);
        GeoLocation full = client.reverseGeocode(33.45834, 126.94271);

        assertThat(partial.isAnalyzable()).isFalse();
        assertThat(full.city()).isEqualTo("서귀포시");
        assertThat(full.placeName()).isEqualTo("성산일출봉");
        server.verify();
    }

    /** 깨진 Nearby 본문(프록시 장애 등)도 일시 실패다 — "결과 없음"으로 캐시되면 그 칸이 주소명으로 굳는다. */
    @Test
    void nearby_응답_파싱_실패는_일시_실패로_보고_캐시하지_않는다() {
        server.expect(ExpectedCount.once(), requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withSuccess("{\"places\": [", MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withSuccess(NEARBY_SEONGSAN, MediaType.APPLICATION_JSON));

        GeoLocation degraded = client.reverseGeocode(33.45, 126.94);
        GeoLocation recovered = client.reverseGeocode(33.45, 126.94);

        assertThat(degraded.placeName()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        assertThat(recovered.placeName()).isEqualTo("성산일출봉");
        server.verify();
    }

    /**
     * 적도·본초자오선 부근 좌표는 {@code Double.toString}이면 {@code 7.0E-5}로 나가 Geocoding이 INVALID_REQUEST로
     * 거부하고 요청 전체가 REVERSE_GEOCODE_FAILED가 된다 — plain 표기여야 한다.
     */
    @Test
    void 작은_좌표도_지수_표기_없이_보낸다() {
        server.expect(requestTo(containsString("latlng=51.4779,0.00007")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andExpect(content().string(containsString("\"longitude\":0.00007")))
                .andExpect(jsonPath("$.locationRestriction.circle.center.latitude").value(51.4779))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.reverseGeocode(51.4779, 0.00007);

        server.verify();
    }

    /**
     * 정수 좌표(40.0)는 Jackson이 BigDecimal의 뒤 0을 떼어 {@code 4E+1}로 직렬화한다(WRITE_BIGDECIMAL_AS_PLAIN이
     * 없으면). 그러면 Nearby가 400이라 그 칸이 조용히 주소명으로 굳는다.
     */
    @Test
    void 정수_좌표도_지수_표기_없이_보낸다() {
        server.expect(requestTo(containsString("latlng=40.0,120.0")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andExpect(content().string(containsString("\"latitude\":40")))
                .andExpect(content().string(containsString("\"longitude\":120")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("E+"))))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.reverseGeocode(40.0, 120.0);

        server.verify();
    }

    /** Nearby가 꺼져 있으면(URL 비움) 반경은 읽지 않는다 — 빈 반경 값이 기동을 막으면 안 된다. */
    @Test
    void nearby가_꺼져_있으면_반경_설정이_비어도_기동한다() {
        client = new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", null, "ko", 3, 4096));
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));

        assertThat(client.reverseGeocode(33.45, 126.94).city()).isEqualTo("서귀포시");
        server.verify();
    }

    /** 2자리 이하는 칸이 1km 이상이라 "한 칸 = 한 장소" 근사가 깨지고, 8자리 이상은 1cm 아래라 적중할 일이 없다. */
    @Test
    void 격자_자릿수가_범위를_벗어나면_기동_시_거부한다() {
        assertThatThrownBy(() -> new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", 100, "ko", 8, 4096)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", 100, "ko", 2, 4096)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 0으로 캐시를 끄려 해도 LruCache가 1 이상을 요구한다 — 설정 키를 넣은 메시지로 기동을 막는다. */
    @Test
    void 캐시_크기가_0이면_기동_시_거부한다() {
        assertThatThrownBy(() -> new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, "", 100, "ko", 3, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cache-max-size");
    }

    /**
     * 행정구역은 다 있는데 formatted_address가 없고 Nearby도 비면 장소명이 비어 전처리에서 걸러진다. 그 결과를
     * 칸에 완성으로 고정하면 그 칸의 사진이 전부 빠지므로, 행정구역만 남기고 다음 사진은 Nearby를 다시 묻는다.
     */
    @Test
    void 장소명이_비는_결과는_완성으로_캐시하지_않고_nearby만_다시_묻는다() {
        String noFormattedAddress = GEOCODE_OK.replace("\"formatted_address\": \"대한민국 제주특별자치도 서귀포시 성산읍\",", "");
        assertThat(noFormattedAddress).doesNotContain("formatted_address");
        server.expect(ExpectedCount.once(), requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(noFormattedAddress, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withSuccess("{\"places\": [{\"displayName\": {\"text\": \"\"}}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withSuccess(NEARBY_SEONGSAN, MediaType.APPLICATION_JSON));

        GeoLocation nameless = client.reverseGeocode(33.45, 126.94);
        GeoLocation named = client.reverseGeocode(33.45, 126.94);

        assertThat(nameless.isAnalyzable()).isFalse();
        assertThat(named.placeName()).isEqualTo("성산일출봉");
        server.verify();
    }

    /** GEOCODE_NEARBY_RADIUS가 빈 값이면 Integer가 null로 바인딩된다 — NPE 대신 원인을 말하고 기동을 막는다. */
    @Test
    void nearby_반경이_없거나_상한을_넘으면_기동_시_거부한다() {
        assertThatThrownBy(() -> new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, NEARBY_URL, null, "ko", 3, 4096)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nearby-radius");
        assertThatThrownBy(() -> new GoogleReverseGeocodeClient(clientBuilder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, NEARBY_URL, 50_001, "ko", 3, 4096)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nearby-radius");
    }

    /** 실패를 캐시하면 그 칸은 재시도 없이 계속 실패한다 — 예외는 캐시하지 않는다. */
    @Test
    void geocoding_실패는_캐시하지_않고_다음_호출에서_다시_시도한다() {
        server.expect(requestTo(containsString("/geocode/json"))).andRespond(withServerError());
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.reverseGeocode(33.45, 126.94)).isInstanceOf(BusinessException.class);
        assertThat(client.reverseGeocode(33.45, 126.94).city()).isEqualTo("서귀포시");
        server.verify();
    }

    /**
     * Nearby의 429/5xx 한 번이 그 칸을 저품질(주소명)로 굳히면 안 된다 — 폴백 결과는 캐시하지 않는다.
     * 단 이미 성공한 Geocoding은 남겨, 다음 사진은 Nearby만 다시 묻는다(Geocoding 비용이 사진 수에 비례하지 않게).
     */
    @Test
    void nearby_일시_실패로_폴백한_결과는_캐시하지_않되_geocoding은_재사용한다() {
        server.expect(ExpectedCount.once(), requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(NEARBY_URL)).andRespond(withServerError());
        server.expect(requestTo(NEARBY_URL))
                .andRespond(withSuccess(NEARBY_SEONGSAN, MediaType.APPLICATION_JSON));

        GeoLocation degraded = client.reverseGeocode(33.45, 126.94);
        GeoLocation recovered = client.reverseGeocode(33.45, 126.94);

        assertThat(degraded.placeName()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        assertThat(recovered.placeName()).isEqualTo("성산일출봉");
        server.verify();
    }

    /** Nearby "결과 없음"은 정상 응답이다 — 캐시해서 같은 칸을 다시 묻지 않는다. */
    @Test
    void nearby_결과없음은_정상_응답이라_캐시한다() {
        server.expect(ExpectedCount.once(), requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.once(), requestTo(NEARBY_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.reverseGeocode(33.45, 126.94);
        client.reverseGeocode(33.45, 126.94);

        server.verify();
    }

    @Test
    void geocoding이_OK가_아니면_REVERSE_GEOCODE_FAILED를_던진다() {
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess("{\"status\":\"REQUEST_DENIED\",\"error_message\":\"key\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.reverseGeocode(1.0, 2.0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVERSE_GEOCODE_FAILED);
    }

    @Test
    void geocoding이_HTTP_5xx면_REVERSE_GEOCODE_FAILED를_던진다() {
        server.expect(requestTo(containsString("/geocode/json"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.reverseGeocode(1.0, 2.0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVERSE_GEOCODE_FAILED);
    }
}
