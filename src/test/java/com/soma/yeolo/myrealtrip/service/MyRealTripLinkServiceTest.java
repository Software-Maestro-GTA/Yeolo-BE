package com.soma.yeolo.myrealtrip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.myrealtrip.client.MyRealTripPartnerClient;
import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.FlightSearch;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import com.soma.yeolo.myrealtrip.domain.Region;
import com.soma.yeolo.myrealtrip.dto.FlightLinkRequest;
import com.soma.yeolo.myrealtrip.dto.ProductLinkRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 제휴 링크 서비스 — 입력 검증(400)·Asia/Seoul 기준 과거 출국일·한도(429)·포트 호출을 fake 포트로 검증한다.
 * 시계는 UTC 2026-11-05T16:00Z 로 고정한다 = 서울 11-06 01:00.
 */
class MyRealTripLinkServiceTest {

    private static final Instant NOW_UTC = Instant.parse("2026-11-05T16:00:00Z");

    private final FakePartnerClient partner = new FakePartnerClient();
    private final MyRealTripRateLimiter limiter = new MyRealTripRateLimiter(2, Duration.ofMinutes(1), Clock.systemUTC());
    private final MyRealTripLinkService service = new MyRealTripLinkService(partner, limiter,
            Clock.fixed(NOW_UTC, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void 항공_링크는_검증된_조건을_포트에_넘기고_제휴_URL을_돌려준다() {
        var response = service.createFlightLink(userId, new FlightLinkRequest("ICN", "NRT", "2026-12-10", "2026-12-12", "2"));

        assertThat(partner.flights).containsExactly(new FlightSearch("ICN", "NRT",
                LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-12"), 2));
        assertThat(response.url()).isEqualTo("https://myrealt.rip/example");
    }

    @Test
    void 공항_코드_앞뒤_공백은_다른_필드와_같이_지운다() {
        service.createFlightLink(userId, new FlightLinkRequest(" ICN", "NRT ", " 2026-12-10", "2026-12-12", " 1 "));
        assertThat(partner.flights.get(0).depAirportCd()).isEqualTo("ICN");
        assertThat(partner.flights.get(0).arrAirportCd()).isEqualTo("NRT");
    }

    @Test
    void 과거_출국일은_서울_기준이고_당일_왕복은_허용된다() {
        assertInvalid(() -> service.createFlightLink(userId, flight("2026-11-05", "2026-11-07")));   // 서울은 이미 11-06
        service.createFlightLink(userId, flight("2026-11-06", "2026-11-06"));                        // 오늘 당일 왕복
        assertThat(partner.flights).hasSize(1);
    }

    @Test
    void 항공_링크_입력_오류는_400이고_파트너를_부르지_않는다() {
        assertInvalid(() -> service.createFlightLink(userId, null));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("icn", "NRT", "2026-12-10", "2026-12-12", "1")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICNX", "NRT", "2026-12-10", "2026-12-12", "1")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICN", "ICN", "2026-12-10", "2026-12-12", "1")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICN", null, "2026-12-10", "2026-12-12", "1")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICN", "NRT", "2026-12-12", "2026-12-10", "1")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICN", "NRT", "2026/12/10", "2026-12-12", "1")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICN", "NRT", "2026-12-10", "2026-12-12", "0")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICN", "NRT", "2026-12-10", "2026-12-12", "1.5")));
        assertInvalid(() -> service.createFlightLink(userId, new FlightLinkRequest("ICN", "NRT", "2026-12-10", "2026-12-12", null)));
        assertThat(partner.flights).isEmpty();
    }

    @Test
    void 상품_링크는_HTTPS_myrealtrip_com_원본_URL만_받고_홈_URL도_허용한다() {
        service.createProductLink(userId, new ProductLinkRequest("https://experiences.myrealtrip.com/products/example"));
        service.createProductLink(userId, new ProductLinkRequest("https://www.myrealtrip.com/"));
        assertThat(partner.targets).containsExactly("https://experiences.myrealtrip.com/products/example",
                "https://www.myrealtrip.com/");

        assertInvalid(() -> service.createProductLink(userId, null));
        assertInvalid(() -> service.createProductLink(userId, new ProductLinkRequest(null)));
        assertInvalid(() -> service.createProductLink(userId, new ProductLinkRequest("http://myrealtrip.com/p")));
        assertInvalid(() -> service.createProductLink(userId, new ProductLinkRequest("https://myrealtrip.com.evil.example/p")));
        assertInvalid(() -> service.createProductLink(userId, new ProductLinkRequest("https://myrealt.rip/already-short")));
        assertThat(partner.targets).hasSize(2);
    }

    @Test
    void 링크_생성도_검색과_같은_사용자_한도를_쓰고_초과_시_파트너를_부르지_않는다() {
        service.createProductLink(userId, new ProductLinkRequest("https://myrealtrip.com/a"));
        service.createFlightLink(userId, flight("2026-12-10", "2026-12-12"));
        assertThatThrownBy(() -> service.createProductLink(userId, new ProductLinkRequest("https://myrealtrip.com/b")))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(partner.targets).hasSize(1);
        assertThat(partner.flights).hasSize(1);
    }

    @Test
    void 인증_주체가_없으면_401이다() {
        assertThatThrownBy(() -> service.createProductLink(null, new ProductLinkRequest("https://myrealtrip.com/a")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private static FlightLinkRequest flight(String dep, String arr) {
        return new FlightLinkRequest("ICN", "NRT", dep, arr, "1");
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MYREALTRIP_REQUEST);
    }

    private static final class FakePartnerClient implements MyRealTripPartnerClient {
        final List<FlightSearch> flights = new ArrayList<>();
        final List<String> targets = new ArrayList<>();

        @Override
        public List<Airport> searchAirports(String keyword) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Region> searchRegions(String keyword, boolean isDomestic) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProductPage searchHotels(HotelSearchQuery query) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProductPage searchTickets(String keyword, int page) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String createFlightLink(FlightSearch search) {
            flights.add(search);
            return "https://myrealt.rip/example";
        }

        @Override
        public String createProductLink(String targetUrl) {
            targets.add(targetUrl);
            return "https://myrealt.rip/example";
        }
    }
}
