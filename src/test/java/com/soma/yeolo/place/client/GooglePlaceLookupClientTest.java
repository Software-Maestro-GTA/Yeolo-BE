package com.soma.yeolo.place.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GooglePlaceLookupClientTest {

    private static final String SEARCH_TEXT_URL = "https://places.googleapis.com/v1/places:searchText";

    private static final PlaceQuery QUERY = new PlaceQuery("성산일출봉", "nature", "대한민국", "제주");

    private static final String SEARCH_OK = """
            {
              "places": [
                {
                  "id": "ChIJ123",
                  "displayName": {"text": "성산일출봉", "languageCode": "ko"},
                  "formattedAddress": "대한민국 제주특별자치도 서귀포시 성산읍",
                  "location": {"latitude": 33.4581, "longitude": 126.9425},
                  "primaryType": "tourist_attraction",
                  "types": ["tourist_attraction", "point_of_interest"]
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
                new GooglePlaceProperties("test-key", SEARCH_TEXT_URL, "ko"));
    }

    @Test
    void 한_번의_검색으로_장소_후보를_만든다() {
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.textQuery").value("성산일출봉, 제주, 대한민국"))
                .andExpect(jsonPath("$.languageCode").value("ko"))
                .andRespond(withSuccess(SEARCH_OK, MediaType.APPLICATION_JSON));

        Place candidate = client.lookup(QUERY).orElseThrow();

        assertThat(candidate.providerPlaceId()).isEqualTo("ChIJ123");
        assertThat(candidate.placeName()).isEqualTo("성산일출봉");
        assertThat(candidate.category()).isEqualTo("tourist_attraction");
        assertThat(candidate.address()).isEqualTo("대한민국 제주특별자치도 서귀포시 성산읍");
        assertThat(candidate.latitude()).isEqualTo(33.4581);
        assertThat(candidate.longitude()).isEqualTo(126.9425);
        assertThat(candidate.rating()).isNull();
        assertThat(candidate.openingHours()).isEmpty();
        server.verify();
    }

    /**
     * 신 API는 키를 쿼리가 아니라 헤더로 받고, 응답 필드를 FieldMask로 지정해야 한다 —
     * FieldMask가 빠지면 400이고, 전체를 요청하면 최상위 과금 티어로 청구된다.
     */
    @Test
    void 키는_헤더로_보내고_필요한_필드만_요청한다() {
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", containsString("places.location")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(SEARCH_OK, MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isPresent();
        server.verify();
    }

    /**
     * 과금 티어 가드. Text Search (New)는 요청 필드 중 가장 비싼 티어로 호출 전체를 청구한다 —
     * 평점·운영시간 하나만 넣어도 Pro(월 무료 5,000건)가 Enterprise(월 무료 1,000건)가 된다.
     * 실제로 2026-11까지 평점·운영시간 때문에 Enterprise로 청구됐고, 그 값은 FE 응답에 쓰이지도
     * 않았다. 금지 목록이 아니라 <b>정확 일치</b>로 잠근다 — Enterprise/Atmosphere 필드는 수십 개라
     * 목록으로는 구멍이 생긴다. 필드를 추가하려면 Google 문서에서 그 필드의 티어를 확인하고
     * 여기 기대값도 함께 바꾼다.
     */
    @Test
    void FieldMask는_Pro_티어_필드만_정확히_요청한다() {
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andExpect(header("X-Goog-FieldMask",
                        "places.id,places.displayName,places.formattedAddress,"
                                + "places.location,places.primaryType,places.types"))
                .andRespond(withSuccess(SEARCH_OK, MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isPresent();
        server.verify();
    }

    /** 사진 URL은 API 키를 요구해 그대로 내보내면 키가 노출된다 — 담지 않는다. */
    @Test
    void 사진은_내려보내지_않는다() {
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andRespond(withSuccess(SEARCH_OK, MediaType.APPLICATION_JSON));

        Place candidate = client.lookup(QUERY).orElseThrow();

        assertThat(candidate.photoUrl()).isNull();
    }

    /** primaryType이 없는 장소도 있다 — 구 API에서 types[0]을 쓰던 것과 같은 값으로 폴백한다. */
    @Test
    void primaryType이_없으면_types의_첫_값을_분류로_쓴다() {
        String body = """
                {"places": [{
                  "id": "ChIJ123",
                  "displayName": {"text": "성산일출봉"},
                  "location": {"latitude": 33.4581, "longitude": 126.9425},
                  "types": ["point_of_interest", "establishment"]
                }]}
                """;
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY).orElseThrow().category()).isEqualTo("point_of_interest");
    }

    /** 신 API는 결과가 없을 때 상태 코드가 아니라 빈 객체를 준다. */
    @Test
    void 결과가_없으면_빈_값을_돌려준다() {
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isEmpty();
        server.verify();
    }

    @Test
    void 좌표가_없는_결과는_사용하지_않는다() {
        String body = """
                {"places": [{"id": "ChIJ123", "displayName": {"text": "성산일출봉"}}]}
                """;
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThat(client.lookup(QUERY)).isEmpty();
    }

    /** 코스 생성 뒤에 붙는 부가 단계이므로, 조회 실패가 예외로 번지면 안 된다. */
    @Test
    void 호출이_실패해도_예외_대신_빈_값을_돌려준다() {
        server.expect(requestTo(SEARCH_TEXT_URL)).andRespond(withServerError());

        assertThat(client.lookup(QUERY)).isEmpty();
    }

    /** 키 제한·API 미활성화(403)도 마찬가지다 — 설정 문제지만 코스 생성을 죽이지는 않는다. */
    @Test
    void 권한_거부여도_예외_대신_빈_값을_돌려준다() {
        server.expect(requestTo(SEARCH_TEXT_URL))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"PERMISSION_DENIED\"}}"));

        assertThat(client.lookup(QUERY)).isEmpty();
    }
}
