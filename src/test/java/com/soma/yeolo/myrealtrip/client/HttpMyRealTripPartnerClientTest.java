package com.soma.yeolo.myrealtrip.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.domain.Product;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import com.soma.yeolo.myrealtrip.domain.Region;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 파트너 API 어댑터 — 요청 형식(경로·키·본문)과 실패 매핑(API-MRT-1/2 §실패 응답)을 실제 호출 없이 고정한다.
 * 파트너 본문 형식의 근거는 이관 전 FE 어댑터(Yeolo-FE {@code myrealtrip.ts})다.
 */
class HttpMyRealTripPartnerClientTest {

    private static final String BASE = "http://partner.test";
    private static final String AIRPORTS = BASE + HttpMyRealTripPartnerClient.AIRPORT_AUTOCOMPLETE_PATH;
    private static final String REGIONS = BASE + HttpMyRealTripPartnerClient.REGION_AUTOCOMPLETE_PATH;
    private static final String HOTELS = BASE + HttpMyRealTripPartnerClient.HOTEL_SEARCH_PATH;
    private static final String TICKETS = BASE + HttpMyRealTripPartnerClient.TICKET_SEARCH_PATH;

    private MockRestServiceServer server;
    private HttpMyRealTripPartnerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpMyRealTripPartnerClient(builder.build(), properties("partner-key"));
    }

    private static MyRealTripProperties properties(String apiKey) {
        return new MyRealTripProperties(BASE, apiKey, 3000, 10000, 60, Duration.ofMinutes(1));
    }

    @Test
    void 공항_검색은_파트너_키와_size_10으로_POST_하고_대문자_3자리_코드만_남긴다() {
        server.expect(once(), requestTo(AIRPORTS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer partner-key"))
                .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(jsonPath("$.keyword").value("인천"))
                .andExpect(jsonPath("$.size").value(10))
                .andRespond(withSuccess("""
                        {"result":{"status":200,"message":"ok"},
                         "data":{"airports":[
                           {"airport":{"code":"ICN","koName":"인천국제공항","enName":"Incheon International Airport","extra":1}},
                           {"airport":{"code":"GMP"}},
                           {"airport":{"code":"icn","koName":"소문자"}},
                           {"airport":{"code":"ICNX"}},
                           {"airport":{}},
                           {"other":true}
                         ]}}
                        """, MediaType.APPLICATION_JSON));

        List<Airport> airports = client.searchAirports("인천");

        assertThat(airports).containsExactly(
                new Airport("ICN", "인천국제공항", "Incheon International Airport"),
                new Airport("GMP", null, null));
        server.verify();
    }

    @Test
    void 숙박_지역_검색은_isDomestic을_그대로_보내고_식별자나_이름이_없는_항목을_버린다() {
        server.expect(once(), requestTo(REGIONS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer partner-key"))
                .andExpect(jsonPath("$.keyword").value("도쿄"))
                .andExpect(jsonPath("$.isDomestic").value(false))
                .andRespond(withSuccess("""
                        {"data":{"regions":[
                           {"regionId":12345,"name":"도쿄","subName":"일본"},
                           {"regionId":67890,"name":"도쿄역"},
                           {"name":"식별자 없음"},
                           {"regionId":1}
                         ]}}
                        """, MediaType.APPLICATION_JSON));

        List<Region> regions = client.searchRegions("도쿄", false);

        assertThat(regions).containsExactly(
                new Region(12345, "도쿄", "일본"),
                new Region(67890, "도쿄역", null));
        server.verify();
    }

    @Test
    void 결과가_없으면_빈_목록이다() {
        server.expect(once(), requestTo(AIRPORTS))
                .andRespond(withSuccess("{\"result\":{\"status\":200},\"data\":{\"airports\":[]}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.searchAirports("없는곳")).isEmpty();
        server.verify();
    }

    @Test
    void 파트너_키가_비어_있으면_호출_없이_500이다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer own = MockRestServiceServer.bindTo(builder).build();
        HttpMyRealTripPartnerClient unconfigured = new HttpMyRealTripPartnerClient(builder.build(), properties("  "));

        assertThatThrownBy(() -> unconfigured.searchAirports("인천"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MYREALTRIP_NOT_CONFIGURED);
        own.verify();   // 기대 호출 0건
    }

    @Test
    void 파트너의_401과_403은_로그인_만료가_아니라_502다() {
        server.expect(once(), requestTo(AIRPORTS)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(once(), requestTo(AIRPORTS)).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        server.verify();
    }

    @Test
    void 파트너의_429는_429로_전달하고_Retry_After가_숫자면_그대로_싣는다() {
        server.expect(once(), requestTo(AIRPORTS))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, "7"));
        server.expect(once(), requestTo(AIRPORTS))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "Wed, 21 Oct 2026 07:28:00 GMT"));
        server.expect(once(), requestTo(AIRPORTS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.searchAirports("인천"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MYREALTRIP_RATE_LIMITED);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(7L);
                });
        assertThatThrownBy(() -> client.searchAirports("인천"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isNull());   // 날짜 형식은 싣지 않는다
        assertThatThrownBy(() -> client.searchAirports("인천"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isNull());
        server.verify();
    }

    @Test
    void 파트너_서버_오류와_연결_실패는_502다() {
        server.expect(once(), requestTo(AIRPORTS)).andRespond(withServerError());
        server.expect(once(), requestTo(AIRPORTS)).andRespond(withException(new ConnectException("refused")));

        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        server.verify();
    }

    @Test
    void 파트너_응답_시간_초과는_504이고_연결_시간_초과는_502다() {
        server.expect(once(), requestTo(AIRPORTS)).andRespond(withException(new SocketTimeoutException("Read timed out")));
        // HttpURLConnection 은 연결 타임아웃도 SocketTimeoutException 으로 던진다 — "응답 지연"이 아니라 "호출 실패"다.
        server.expect(once(), requestTo(AIRPORTS)).andRespond(withException(new SocketTimeoutException("Connect timed out")));

        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_TIMEOUT);
        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        server.verify();
    }

    @Test
    void base_url_끝의_슬래시는_지워_이중_슬래시_경로를_만들지_않는다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer own = MockRestServiceServer.bindTo(builder).build();
        HttpMyRealTripPartnerClient slashed = new HttpMyRealTripPartnerClient(builder.build(),
                new MyRealTripProperties(BASE + "/", "partner-key", 3000, 10000, 60, Duration.ofMinutes(1)));
        own.expect(once(), requestTo(AIRPORTS))
                .andRespond(withSuccess("{\"data\":{\"airports\":[]}}", MediaType.APPLICATION_JSON));

        assertThat(slashed.searchAirports("인천")).isEmpty();
        own.verify();
    }

    @Test
    void result_status가_200이_아니거나_data나_필수_배열이_없으면_빈_결과로_숨기지_않고_502다() {
        server.expect(once(), requestTo(AIRPORTS))
                .andRespond(withSuccess("{\"result\":{\"status\":500},\"data\":{\"airports\":[]}}",
                        MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(AIRPORTS))
                .andRespond(withSuccess("{\"result\":{\"status\":200}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(AIRPORTS))
                .andRespond(withSuccess("{\"result\":{\"status\":200},\"data\":{}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(REGIONS))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        assertCode(() -> client.searchAirports("인천"), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        assertCode(() -> client.searchRegions("도쿄", true), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        server.verify();
    }

    @Test
    void 숙소_조회는_페이지를_0부터로_바꿔_size_20과_함께_보내고_유효한_상품만_남긴다() {
        server.expect(once(), requestTo(HOTELS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer partner-key"))
                .andExpect(jsonPath("$.regionId").value(12345))
                .andExpect(jsonPath("$.checkIn").value("2026-12-10"))
                .andExpect(jsonPath("$.checkOut").value("2026-12-12"))
                .andExpect(jsonPath("$.adultCount").value(2))
                .andExpect(jsonPath("$.page").value(1))     // FE→BE 2 페이지 = 상위 1 페이지
                .andExpect(jsonPath("$.size").value(20))
                .andRespond(withSuccess("""
                        {"result":{"status":200},"data":{"totalCount":45,"page":1,"items":[
                          {"itemName":"호텔 A","productUrl":"https://accommodation.myrealtrip.com/union/products/a",
                           "salePrice":120000,"imageUrl":"https://img.myrealtrip.com/a.jpg","starRating":4,
                           "reviewScore":"4.5","reviewCount":120,"category":"hotel","tags":["x"],"priceDisplay":"12만원"},
                          {"itemName":"호텔 B","productUrl":"https://accommodation.myrealtrip.com/union/products/b",
                           "salePrice":0,"reviewScore":4.7},
                          {"itemName":"HTTP 링크","productUrl":"http://accommodation.myrealtrip.com/x","salePrice":1000},
                          {"itemName":"유사 도메인","productUrl":"https://myrealtrip.com.evil.example/x","salePrice":1000},
                          {"itemName":"음수 가격","productUrl":"https://myrealtrip.com/x","salePrice":-1},
                          {"itemName":"문자열 가격","productUrl":"https://myrealtrip.com/x","salePrice":"12000"},
                          {"productUrl":"https://myrealtrip.com/x","salePrice":1000},
                          "문자열 항목", null
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        ProductPage page = client.searchHotels(new HotelSearchQuery(12345, LocalDate.parse("2026-12-10"),
                LocalDate.parse("2026-12-12"), 2, 2));

        assertThat(page.page()).isEqualTo(2);
        assertThat(page.totalCount()).isEqualTo(45L);
        assertThat(page.hasMore()).isTrue();     // 2*20 < 45
        assertThat(page.items()).containsExactly(
                new Product("호텔 A", "https://accommodation.myrealtrip.com/union/products/a", new BigDecimal("120000"),
                        "https://img.myrealtrip.com/a.jpg", null, new BigDecimal("4"), "4.5", 120),
                new Product("호텔 B", "https://accommodation.myrealtrip.com/union/products/b", new BigDecimal("0"),
                        null, null, null, 4.7, null));
        server.verify();
    }

    @Test
    void 투어_티켓_검색은_페이지를_그대로_보내고_전체_건수가_없으면_원본_페이지_크기로_다음_페이지를_판단한다() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            items.append(i > 0 ? "," : "")
                    .append("{\"itemName\":\"티켓 ").append(i).append("\",\"productUrl\":\"https://experiences.myrealtrip.com/products/")
                    .append(i).append("\",\"salePrice\":30000,\"description\":\"설명\"")
                    .append(i == 0 ? ",\"productUrl\":\"http://bad\"" : "")     // 첫 항목은 걸러진다
                    .append("}");
        }
        server.expect(once(), requestTo(TICKETS))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.keyword").value("도쿄"))
                .andExpect(jsonPath("$.page").value(3))
                .andExpect(jsonPath("$.size").value(20))
                .andRespond(withSuccess("{\"data\":{\"items\":[" + items + "]}}", MediaType.APPLICATION_JSON));

        ProductPage page = client.searchTickets("도쿄", 3);

        assertThat(page.page()).isEqualTo(3);
        assertThat(page.totalCount()).isNull();
        assertThat(page.items()).hasSize(19);
        assertThat(page.items().get(0).description()).isEqualTo("설명");
        assertThat(page.hasMore()).isTrue();     // 필터링 전 원본이 20개
        server.verify();
    }

    @Test
    void 숙소_첫_페이지는_상위_0페이지이고_정수값_실수_건수는_정수로_읽고_빈_상품명은_제외한다() {
        server.expect(once(), requestTo(HOTELS))
                .andExpect(jsonPath("$.page").value(0))
                .andRespond(withSuccess("""
                        {"data":{"totalCount":45.0,"items":[
                          {"itemName":"","productUrl":"https://myrealtrip.com/x","salePrice":1000},
                          {"itemName":"  ","productUrl":"https://myrealtrip.com/x","salePrice":1000},
                          {"itemName":"A","productUrl":"https://myrealtrip.com/a","salePrice":1000,"reviewCount":120.0,"starRating":4.5,"imageUrl":"","description":" "},
                          {"itemName":"B","productUrl":"https://myrealtrip.com/b","salePrice":12000000.0,"reviewCount":-1}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        ProductPage page = client.searchHotels(new HotelSearchQuery(1, LocalDate.parse("2026-12-10"),
                LocalDate.parse("2026-12-12"), 1, 1));

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalCount()).isEqualTo(45L);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).reviewCount()).isEqualTo(120);
        assertThat(page.items().get(0).starRating()).isEqualByComparingTo("4.5");
        assertThat(page.items().get(0).imageUrl()).isNull();        // 빈 문자열은 "없음"
        assertThat(page.items().get(0).description()).isNull();
        assertThat(page.items().get(1).reviewCount()).isNull();     // 음수 리뷰 수는 모름
        assertThat(page.items().get(1).salePrice().toString()).isEqualTo("12000000");   // 1.2E+7 로 나가지 않는다
        server.verify();
    }

    @Test
    void 전체_건수가_음수나_비정수면_미제공으로_본다() {
        server.expect(once(), requestTo(TICKETS))
                .andRespond(withSuccess("{\"data\":{\"totalCount\":-1,\"items\":[]}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TICKETS))
                .andRespond(withSuccess("{\"data\":{\"totalCount\":\"many\",\"items\":[]}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TICKETS))
                .andRespond(withSuccess("{\"data\":{\"totalCount\":45.5,\"items\":[]}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TICKETS))   // Jackson 은 1e999 를 무한대로 읽는다 — 500 이 아니라 "미제공"
                .andRespond(withSuccess("{\"data\":{\"totalCount\":1e999,\"items\":[{\"itemName\":\"A\",\"productUrl\":\"https://myrealtrip.com/a\",\"salePrice\":1,\"reviewCount\":1e999}]}}", MediaType.APPLICATION_JSON));

        assertThat(client.searchTickets("도쿄", 1).totalCount()).isNull();
        assertThat(client.searchTickets("도쿄", 1).totalCount()).isNull();
        assertThat(client.searchTickets("도쿄", 1).totalCount()).isNull();
        ProductPage infinite = client.searchTickets("도쿄", 1);
        assertThat(infinite.totalCount()).isNull();
        assertThat(infinite.items()).hasSize(1);
        assertThat(infinite.items().get(0).reviewCount()).isNull();
        server.verify();
    }

    @Test
    void items_배열이_없거나_배열이_아니면_빈_결과로_숨기지_않고_502다() {
        server.expect(once(), requestTo(HOTELS))
                .andRespond(withSuccess("{\"data\":{\"totalCount\":3}}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(TICKETS))
                .andRespond(withSuccess("{\"data\":{\"items\":{\"a\":1}}}", MediaType.APPLICATION_JSON));

        HotelSearchQuery query = new HotelSearchQuery(1, LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-12"), 1, 1);
        assertCode(() -> client.searchHotels(query), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        assertCode(() -> client.searchTickets("도쿄", 1), ErrorCode.MYREALTRIP_PARTNER_ERROR);
        server.verify();
    }

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
