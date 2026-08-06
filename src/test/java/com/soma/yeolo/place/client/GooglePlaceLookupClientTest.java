package com.soma.yeolo.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePlaceLookupClientTest {

    private static final String TEXT_SEARCH_URL =
            "https://maps.googleapis.com/maps/api/place/textsearch/json";
    private static final String DETAILS_URL =
            "https://maps.googleapis.com/maps/api/place/details/json";

    private static final PlaceQuery QUERY = new PlaceQuery("성산일출봉", "nature", "대한민국", "제주");

    private static final String SEARCH_OK = """
            {
              "status": "OK",
              "results": [
                {
                  "place_id": "ChIJ123",
                  "name": "성산일출봉",
                  "formatted_address": "대한민국 제주특별자치도 서귀포시 성산읍",
                  "geometry": {"location": {"lat": 33.4581, "lng": 126.9425}},
                  "rating": 4.6,
                  "types": ["tourist_attraction", "point_of_interest"],
                  "photos": [{"photo_reference": "PHOTO_REF"}]
                }
              ]
            }
            """;

    private MockRestServiceServer server;
    private GooglePlaceLookupClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlaceLookupClient(builder.build(),
                new GooglePlaceProperties("test-key", TEXT_SEARCH_URL, DETAILS_URL, "ko"));
    }

    @Test
    void 검색과_상세를_조합해_장소_후보를_만든다() {
        String details = """
                {"status": "OK", "result": {"opening_hours": {"weekday_text":
                  ["월요일: 07:00~20:00", "화요일: 07:00~20:00"]}}}
                """;
        server.expect(requestTo(containsString("/textsearch/json")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(SEARCH_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("place_id=ChIJ123")))
                .andRespond(withSuccess(details, MediaType.APPLICATION_JSON));

        Place candidate = client.lookup(QUERY).orElseThrow();

        assertThat(candidate.providerPlaceId()).isEqualTo("ChIJ123");
        assertThat(candidate.placeName()).isEqualTo("성산일출봉");
        assertThat(candidate.category()).isEqualTo("tourist_attraction");
        assertThat(candidate.address()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        assertThat(candidate.latitude()).isEqualTo(33.4581);
        assertThat(candidate.longitude()).isEqualTo(126.9425);
        assertThat(candidate.rating()).isEqualTo(4.6);
        assertThat(candidate.openingHours())
                .containsExactly("월요일: 07:00~20:00", "화요일: 07:00~20:00");
        server.verify();
    }

    /** 사진 URL은 API 키를 쿼리로 요구해 그대로 내보내면 키가 노출된다 — 담지 않는다. */
    @Test
    void 사진은_내려보내지_않는다() {
        server.expect(requestTo(containsString("/textsearch/json")))
                .andRespond(withSuccess(SEARCH_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/details/json")))
                .andRespond(withSuccess("{\"status\":\"OK\",\"result\":{}}",
                        MediaType.APPLICATION_JSON));

        Place candidate = client.lookup(QUERY).orElseThrow();

        assertThat(candidate.photoUrls()).isEmpty();
    }

    @Test
    void 상세_조회가_실패해도_검색_결과로_후보를_돌려준다() {
        server.expect(requestTo(containsString("/textsearch/json")))
                .andRespond(withSuccess(SEARCH_OK, MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("/details/json")))
                .andRespond(withServerError());

        Place candidate = client.lookup(QUERY).orElseThrow();

        assertThat(candidate.providerPlaceId()).isEqualTo("ChIJ123");
        assertThat(candidate.openingHours()).isEmpty();
    }

    @Test
    void 결과가_없으면_빈_값을_돌려준다() {
        server.expect(requestTo(containsString("/textsearch/json")))
                .andRespond(withSuccess("{\"status\":\"ZERO_RESULTS\",\"results\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isEmpty();
        server.verify();
    }

    @Test
    void 좌표가_없는_결과는_사용하지_않는다() {
        String body = """
                {"status": "OK", "results": [{"place_id": "ChIJ123", "name": "성산일출봉"}]}
                """;
        server.expect(requestTo(containsString("/textsearch/json")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isEmpty();
    }

    /** 코스 생성 뒤에 붙는 부가 단계이므로, 조회 실패가 예외로 번지면 안 된다. */
    @Test
    void 호출이_실패해도_예외_대신_빈_값을_돌려준다() {
        server.expect(requestTo(containsString("/textsearch/json"))).andRespond(withServerError());

        assertThat(client.lookup(QUERY)).isEmpty();
    }
}
