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
import com.soma.yeolo.myrealtrip.domain.Region;
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

    private static void assertCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }
}
