package com.soma.yeolo.tasteprofile.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsmReverseGeocodeClientTest {

    private static final String REVERSE_URL = "https://nominatim.openstreetmap.org/reverse";
    private static final String USER_AGENT = "Yeolo-BE-Test/1.0 (contact: test@yeolo.app)";

    private MockRestServiceServer server;
    private OsmReverseGeocodeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // 테스트에서는 호출 간격(minIntervalMs)을 0으로 둬 레이트리밋 대기 없이 검증한다.
        client = new OsmReverseGeocodeClient(builder.build(),
                new OsmGeocodeProperties(REVERSE_URL, USER_AGENT, 18, "ko", 0L, 4096));
    }

    @Test
    void 행정구역과_장소명_유형을_한번의_호출로_채운다() {
        String reverse = """
                {
                  "name": "성산일출봉",
                  "display_name": "성산일출봉, 일출로, 성산읍, 서귀포시, 제주특별자치도, 대한민국",
                  "category": "tourism",
                  "type": "attraction",
                  "address": {
                    "tourism": "성산일출봉",
                    "road": "일출로",
                    "village": "성산읍",
                    "county": "서귀포시",
                    "city": "서귀포시",
                    "state": "제주특별자치도",
                    "country": "대한민국",
                    "country_code": "kr"
                  }
                }
                """;
        server.expect(requestTo(containsString("/reverse")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess(reverse, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        assertThat(location.country()).isEqualTo("대한민국");
        assertThat(location.region()).isEqualTo("제주특별자치도");
        assertThat(location.city()).isEqualTo("서귀포시");
        // city가 서귀포시로 채워졌으므로 district는 같은 값(county) 대신 village(성산읍)를 쓴다.
        assertThat(location.district()).isEqualTo("성산읍");
        assertThat(location.placeName()).isEqualTo("성산일출봉");
        assertThat(location.placeTypes()).containsExactly("tourism", "attraction");
        server.verify();
    }

    @Test
    void 특별시는_display_name으로_region까지_채운다() {
        // 서울같은 특별시는 address 블록에 state/province가 없어 예전엔 region이 null이었다.
        // display_name(뒤에서부터: 대한민국, 우편번호, 시·도, 구, 동 ...)으로 region을 채운다.
        String reverse = """
                {
                  "name": "키친인큐베이터",
                  "display_name": "키친인큐베이터, 21, 백범로31길, 아현동, 마포구, 서울특별시, 04147, 대한민국",
                  "category": "amenity",
                  "type": "restaurant",
                  "address": { "city": "서울특별시", "borough": "마포구", "country": "대한민국" }
                }
                """;
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess(reverse, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(37.5465, 126.9498);

        assertThat(location.country()).isEqualTo("대한민국");
        assertThat(location.region()).isEqualTo("서울특별시");   // 예전엔 null
        assertThat(location.city()).isEqualTo("마포구");
        assertThat(location.district()).isEqualTo("아현동");
        assertThat(location.placeName()).isEqualTo("키친인큐베이터");
        server.verify();
    }

    @Test
    void 도_지역은_시도와_시를_구분해_채운다() {
        // 도(道) 있는 지역은 한 단계 더 깊다: 대한민국 · 우편번호 · 경상북도(시·도) · 구미시(시) · 송정동.
        String reverse = """
                {
                  "name": "구미시청",
                  "display_name": "구미시청, 송원동로, 송정동, 구미시, 경상북도, 39280, 대한민국",
                  "category": "office",
                  "type": "government",
                  "address": { "city": "구미시", "province": "경상북도", "country": "대한민국" }
                }
                """;
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess(reverse, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(36.1195, 128.3446);

        assertThat(location.country()).isEqualTo("대한민국");
        assertThat(location.region()).isEqualTo("경상북도");
        assertThat(location.city()).isEqualTo("구미시");
        assertThat(location.district()).isEqualTo("송정동");
        assertThat(location.placeName()).isEqualTo("구미시청");
        server.verify();
    }

    @Test
    void 우편번호가_없어도_행정구역을_매핑한다() {
        // display_name 끝이 곧바로 country면 우편번호 건너뛰기 없이 그 앞을 region으로 본다.
        String reverse = """
                {
                  "name": "성산일출봉",
                  "display_name": "성산일출봉, 성산읍, 서귀포시, 제주특별자치도, 대한민국",
                  "category": "tourism",
                  "type": "attraction"
                }
                """;
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess(reverse, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        assertThat(location.region()).isEqualTo("제주특별자치도");
        assertThat(location.city()).isEqualTo("서귀포시");
        assertThat(location.district()).isEqualTo("성산읍");
        server.verify();
    }

    @Test
    void name이_없으면_display_name으로_장소명을_폴백한다() {
        String reverse = """
                {
                  "display_name": "일출로, 성산읍, 서귀포시, 제주특별자치도, 대한민국",
                  "category": "highway",
                  "type": "residential",
                  "address": {
                    "road": "일출로",
                    "city": "서귀포시",
                    "state": "제주특별자치도",
                    "country": "대한민국"
                  }
                }
                """;
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess(reverse, MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        // name이 없으면 display_name의 최상세(첫) 조각을 장소명으로 쓴다.
        assertThat(location.placeName()).isEqualTo("일출로");
        assertThat(location.placeTypes()).containsExactly("highway", "residential");
        server.verify();
    }

    @Test
    void 좌표에_결과가_없으면_빈_위치를_반환한다() {
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess("{\"error\":\"Unable to geocode\"}", MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(0.0, 0.0);

        assertThat(location.country()).isNull();
        assertThat(location.city()).isNull();
        assertThat(location.placeName()).isNull();
        assertThat(location.placeTypes()).isEmpty();
        server.verify();
    }

    @Test
    void HTTP_5xx면_REVERSE_GEOCODE_FAILED를_던진다() {
        server.expect(requestTo(containsString("/reverse"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.reverseGeocode(1.0, 2.0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVERSE_GEOCODE_FAILED);
    }

    @Test
    void 응답_파싱에_실패하면_REVERSE_GEOCODE_FAILED를_던진다() {
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.reverseGeocode(1.0, 2.0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVERSE_GEOCODE_FAILED);
    }

    @Test
    void 같은_좌표_재조회는_캐시로_HTTP_호출을_생략한다() {
        // 서버 응답은 단 한 번만 준비한다. 두 번째 조회가 캐시를 쓰지 않으면
        // "No further requests expected"로 실패한다.
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess("{\"name\":\"성산일출봉\",\"display_name\":\"성산일출봉, 성산읍, 서귀포시, 제주특별자치도, 대한민국\"}",
                        MediaType.APPLICATION_JSON));

        GeoLocation first = client.reverseGeocode(33.4587, 126.9426);
        GeoLocation second = client.reverseGeocode(33.4587, 126.9426);

        assertThat(second.placeName()).isEqualTo("성산일출봉");
        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    void HTTP_429면_백오프후_재시도해_성공한다() {
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(containsString("/reverse")))
                .andRespond(withSuccess("{\"name\":\"성산일출봉\",\"display_name\":\"성산일출봉, 성산읍, 서귀포시, 제주특별자치도, 대한민국\"}",
                        MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        assertThat(location.placeName()).isEqualTo("성산일출봉");
        assertThat(location.country()).isEqualTo("대한민국");
        server.verify();
    }

    @Test
    void HTTP_429가_재시도_한도까지_계속되면_REVERSE_GEOCODE_FAILED를_던진다() {
        // 최초 1회 + 재시도 2회 = 총 3회 모두 429.
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(containsString("/reverse")))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        }

        assertThatThrownBy(() -> client.reverseGeocode(1.0, 2.0))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVERSE_GEOCODE_FAILED);
        server.verify();
    }
}
