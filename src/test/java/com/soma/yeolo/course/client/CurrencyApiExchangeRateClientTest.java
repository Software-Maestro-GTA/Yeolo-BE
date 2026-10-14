package com.soma.yeolo.course.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** fawazahmed0/currency-api 어댑터 — 파싱, 통화별 캐시, 폴백 호스트, 지난 값 유지. */
class CurrencyApiExchangeRateClientTest {

    private static final String PRIMARY = "http://cdn/currencies";
    private static final String FALLBACK = "http://mirror/currencies";
    private static final String JPY_TABLE = "{\"date\":\"2026-10-14\",\"jpy\":{\"krw\":8.49,\"usd\":0.0063,\"note\":\"x\"}}";

    private MockRestServiceServer server;
    private CurrencyApiExchangeRateClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new CurrencyApiExchangeRateClient(builder.build(),
                new ExchangeRateProperties("KRW", "currency-api", PRIMARY, FALLBACK, Duration.ofHours(12)));
    }

    @Test
    void 기준_통화_표에서_환율을_읽고_같은_통화는_1이다() {
        server.expect(once(), requestTo(PRIMARY + "/jpy.min.json"))
                .andRespond(withSuccess(JPY_TABLE, MediaType.APPLICATION_JSON));

        assertThat(client.rate("JPY", "KRW")).contains(new BigDecimal("8.49"));
        assertThat(client.rate("jpy", "usd")).contains(new BigDecimal("0.0063"));   // 캐시 — 재호출 없음
        assertThat(client.rate("JPY", "JPY")).contains(BigDecimal.ONE);
        assertThat(client.rate("JPY", "XXX")).isEmpty();                            // 표에 없는 통화
        server.verify();
    }

    @Test
    void 기본_호스트가_실패하면_폴백_호스트를_쓴다() {
        server.expect(once(), requestTo(PRIMARY + "/jpy.min.json")).andRespond(withServerError());
        server.expect(once(), requestTo(FALLBACK + "/jpy.min.json"))
                .andRespond(withSuccess(JPY_TABLE, MediaType.APPLICATION_JSON));

        assertThat(client.rate("JPY", "KRW")).contains(new BigDecimal("8.49"));
        server.verify();
    }

    @Test
    void 갱신에_실패하면_지난_성공값을_계속_쓴다() {
        // TTL 0 → 매 호출 재조회. 두 번째 조회가 실패해도 첫 성공값을 돌려준다(어제 환율이 없는 것보다 낫다).
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer own = MockRestServiceServer.bindTo(builder).build();
        CurrencyApiExchangeRateClient shortLived = new CurrencyApiExchangeRateClient(builder.build(),
                new ExchangeRateProperties("KRW", "currency-api", PRIMARY, "", Duration.ZERO));
        own.expect(once(), requestTo(PRIMARY + "/jpy.min.json"))
                .andRespond(withSuccess(JPY_TABLE, MediaType.APPLICATION_JSON));
        own.expect(once(), requestTo(PRIMARY + "/jpy.min.json")).andRespond(withServerError());

        assertThat(shortLived.rate("JPY", "KRW")).contains(new BigDecimal("8.49"));
        assertThat(shortLived.rate("JPY", "KRW")).contains(new BigDecimal("8.49"));
        own.verify();
    }

    /** 실패 직후의 재호출은 백오프 안이라 제공처를 다시 두드리지 않는다 — 장애 중 상세 조회가 느려지지 않게. */
    @Test
    void 성공한_적이_없고_모두_실패하면_빈_값이고_백오프_동안_재시도하지_않는다() {
        server.expect(once(), requestTo(PRIMARY + "/jpy.min.json")).andRespond(withServerError());
        server.expect(once(), requestTo(FALLBACK + "/jpy.min.json")).andRespond(withServerError());

        assertThat(client.rate("JPY", "KRW")).isEmpty();
        assertThat(client.rate("JPY", "KRW")).isEmpty();   // 기대 큐에 더 없다 — 재호출하면 verify 가 실패한다
        assertThat(client.rate("JPY", "USD")).isEmpty();
        server.verify();
    }

    @Test
    void 빈_통화_코드는_조회_없이_빈_값이다() {
        assertThat(client.rate("", "KRW")).isEmpty();
        assertThat(client.rate("JPY", null)).isEmpty();
        server.verify();
    }

    @Test
    void currency_api_설정은_base_url과_ttl이_비면_기동_시_실패한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new ExchangeRateProperties("KRW", "currency-api", "", FALLBACK, Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new ExchangeRateProperties("KRW", "currency-api", PRIMARY, FALLBACK, null))
                .isInstanceOf(IllegalStateException.class);
        // 원화 전제(source-currency)는 provider 와 무관하게 ISO 코드여야 한다.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> new ExchangeRateProperties("원", "stub", null, null, null))
                .isInstanceOf(IllegalStateException.class);
        // stub 이면 네트워크 설정은 검증하지 않는다 — 없어도 기동한다.
        assertThat(new ExchangeRateProperties("krw", "stub", null, null, null).sourceCurrency()).isEqualTo("KRW");
    }

    @Test
    void 표가_없는_응답은_실패로_본다() {
        server.expect(once(), requestTo(PRIMARY + "/jpy.min.json"))
                .andRespond(withSuccess("{\"date\":\"2026-10-14\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(FALLBACK + "/jpy.min.json"))
                .andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThat(client.rate("JPY", "KRW")).isEmpty();
        server.verify();
    }
}
