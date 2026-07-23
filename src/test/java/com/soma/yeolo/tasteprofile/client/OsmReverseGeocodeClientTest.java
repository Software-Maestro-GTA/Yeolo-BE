package com.soma.yeolo.tasteprofile.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.domain.GeoLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
        client = new OsmReverseGeocodeClient(builder.build(),
                new OsmGeocodeProperties(REVERSE_URL, USER_AGENT, 18, "ko"));
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

        assertThat(location.placeName()).isEqualTo("일출로, 성산읍, 서귀포시, 제주특별자치도, 대한민국");
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
}
