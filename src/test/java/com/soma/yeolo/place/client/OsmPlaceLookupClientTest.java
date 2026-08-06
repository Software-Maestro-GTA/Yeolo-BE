package com.soma.yeolo.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soma.yeolo.global.client.IntervalRateLimiter;
import com.soma.yeolo.global.client.NominatimProperties;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OsmPlaceLookupClientTest {

    private static final String SEARCH_URL = "https://nominatim.openstreetmap.org/search";
    private static final String USER_AGENT = "Yeolo-BE-Test/1.0 (contact: test@yeolo.app)";

    private static final PlaceQuery QUERY = new PlaceQuery("성산일출봉", "nature", "대한민국", "제주");

    private MockRestServiceServer server;
    private OsmPlaceLookupClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // 테스트에서는 호출 간격을 0으로 둬 레이트리밋 대기 없이 검증한다.
        client = new OsmPlaceLookupClient(builder.build(),
                new OsmPlaceProperties(SEARCH_URL, "ko"),
                new NominatimProperties(USER_AGENT, 0L),
                new IntervalRateLimiter(0L));
    }

    @Test
    void 검색_결과를_장소_후보로_변환한다() {
        String body = """
                [
                  {
                    "place_id": 123456,
                    "osm_type": "node",
                    "osm_id": 987654,
                    "lat": "33.4581",
                    "lon": "126.9425",
                    "name": "성산일출봉",
                    "display_name": "성산일출봉, 성산읍, 서귀포시, 제주특별자치도, 대한민국",
                    "category": "tourism",
                    "type": "attraction",
                    "extratags": {"opening_hours": "Mo-Su 07:00-20:00"}
                  }
                ]
                """;
        server.expect(requestTo(containsString("/search")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.USER_AGENT, USER_AGENT))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Place candidate = client.lookup(QUERY).orElseThrow();

        assertThat(candidate.providerPlaceId()).isEqualTo("osm:n987654");
        assertThat(candidate.placeName()).isEqualTo("성산일출봉");
        assertThat(candidate.category()).isEqualTo("attraction");
        assertThat(candidate.address())
                .isEqualTo("성산일출봉, 성산읍, 서귀포시, 제주특별자치도, 대한민국");
        assertThat(candidate.latitude()).isEqualTo(33.4581);
        assertThat(candidate.longitude()).isEqualTo(126.9425);
        // Nominatim은 평점·사진을 제공하지 않는다.
        assertThat(candidate.rating()).isNull();
        assertThat(candidate.photoUrls()).isEmpty();
        assertThat(candidate.openingHours()).containsExactly("Mo-Su 07:00-20:00");
        server.verify();
    }

    @Test
    void 목적지를_붙인_검색어로_질의한다() {
        server.expect(requestTo(containsString("q=%EC%84%B1%EC%82%B0%EC%9D%BC%EC%B6%9C%EB%B4%89")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isEmpty();
        server.verify();
    }

    @Test
    void 운영시간_정보가_없으면_빈_목록으로_돌려준다() {
        String body = """
                [{"osm_type": "way", "osm_id": 42, "lat": "33.39", "lon": "126.23",
                  "display_name": "협재 해수욕장", "type": "beach"}]
                """;
        server.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        Place candidate = client.lookup(QUERY).orElseThrow();

        assertThat(candidate.providerPlaceId()).isEqualTo("osm:w42");
        // name이 없으면 AI가 준 장소명을 유지한다.
        assertThat(candidate.placeName()).isEqualTo("성산일출봉");
        assertThat(candidate.openingHours()).isEmpty();
    }

    @Test
    void 결과가_없으면_빈_값을_돌려준다() {
        server.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isEmpty();
    }

    @Test
    void 좌표가_없는_결과는_사용하지_않는다() {
        server.expect(requestTo(containsString("/search")))
                .andRespond(withSuccess("[{\"osm_type\":\"node\",\"osm_id\":1}]",
                        MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isEmpty();
    }

    /** 코스 생성 뒤에 붙는 부가 단계이므로, 조회 실패가 예외로 번지면 안 된다. */
    @Test
    void 호출이_실패해도_예외_대신_빈_값을_돌려준다() {
        server.expect(requestTo(containsString("/search"))).andRespond(withServerError());

        Optional<Place> candidate = client.lookup(QUERY);

        assertThat(candidate).isEmpty();
    }
}
