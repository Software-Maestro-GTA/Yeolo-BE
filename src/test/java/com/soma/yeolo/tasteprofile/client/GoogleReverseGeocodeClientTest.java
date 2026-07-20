package com.soma.yeolo.tasteprofile.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleReverseGeocodeClientTest {

    private static final String GEOCODE_URL = "https://maps.googleapis.com/maps/api/geocode/json";
    private static final String NEARBY_URL = "https://maps.googleapis.com/maps/api/place/nearbysearch/json";

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

    private MockRestServiceServer server;
    private GoogleReverseGeocodeClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleReverseGeocodeClient(builder.build(),
                new GoogleGeocodeProperties("test-key", GEOCODE_URL, NEARBY_URL, 100, "ko"));
    }

    @Test
    void 행정구역은_geocoding_장소명유형은_nearby로_채운다() {
        String nearby = """
                {
                  "results": [{
                    "name": "성산일출봉",
                    "types": ["tourist_attraction","point_of_interest","establishment"]
                  }],
                  "status": "OK"
                }
                """;
        server.expect(requestTo(containsString("/geocode/json")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(GEOCODE_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/nearbysearch/json")))
                .andExpect(method(HttpMethod.GET))
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
        server.expect(requestTo(containsString("/nearbysearch/json")))
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
        server.expect(requestTo(containsString("/nearbysearch/json")))
                .andRespond(withSuccess("{\"results\":[],\"status\":\"ZERO_RESULTS\"}",
                        MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(33.45, 126.94);

        assertThat(location.placeName()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        assertThat(location.placeTypes()).containsExactly("political");
    }

    @Test
    void geocoding이_결과없음이면_미상_위치를_반환한다() {
        server.expect(requestTo(containsString("/geocode/json")))
                .andRespond(withSuccess("{\"results\":[],\"status\":\"ZERO_RESULTS\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/nearbysearch/json")))
                .andRespond(withSuccess("{\"results\":[],\"status\":\"ZERO_RESULTS\"}",
                        MediaType.APPLICATION_JSON));

        GeoLocation location = client.reverseGeocode(0.0, 0.0);

        assertThat(location.country()).isNull();
        assertThat(location.placeName()).isNull();
        assertThat(location.placeTypes()).isEmpty();
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
