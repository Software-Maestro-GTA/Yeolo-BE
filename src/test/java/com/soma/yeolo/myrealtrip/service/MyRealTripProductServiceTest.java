package com.soma.yeolo.myrealtrip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.myrealtrip.client.MyRealTripPartnerClient;
import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.domain.Product;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import com.soma.yeolo.myrealtrip.domain.Region;
import com.soma.yeolo.myrealtrip.dto.HotelSearchResponse;
import com.soma.yeolo.myrealtrip.dto.ProductPageResponse;
import com.soma.yeolo.myrealtrip.dto.TicketSearchResponse;
import java.math.BigDecimal;
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
 * 상품 목록 서비스 — 입력 검증(400)·Asia/Seoul 기준 과거 날짜·페이지 기본값·한도(429)·응답 매핑을 fake 포트로 검증한다.
 * 시계는 UTC 2026-11-05T16:00Z 로 고정한다 = 서울 11-06 01:00. 서울 기준 오늘(11-06)은 통과, 11-05 는 과거다.
 */
class MyRealTripProductServiceTest {

    private static final Instant NOW_UTC = Instant.parse("2026-11-05T16:00:00Z");

    private final FakePartnerClient partner = new FakePartnerClient();
    private final MyRealTripRateLimiter limiter = new MyRealTripRateLimiter(2, Duration.ofMinutes(1), Clock.systemUTC());
    private final MyRealTripProductService service = new MyRealTripProductService(partner, limiter,
            Clock.fixed(NOW_UTC, ZoneOffset.UTC));
    private final UUID userId = UUID.randomUUID();

    @Test
    void 숙소_조회는_검증된_조건을_포트에_넘기고_페이지_틀_그대로_응답한다() {
        partner.page = ProductPage.of(List.of(product("호텔 A")), 2, 45L, 20);

        ProductPageResponse<HotelSearchResponse.Item> response =
                service.searchHotels(userId, "12345", "2026-12-10", "2026-12-12", "2", "2");

        assertThat(partner.hotelQueries).containsExactly(new HotelSearchQuery(12345,
                LocalDate.parse("2026-12-10"), LocalDate.parse("2026-12-12"), 2, 2));
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.totalCount()).isEqualTo(45L);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).itemName()).isEqualTo("호텔 A");
        assertThat(response.items().get(0).starRating()).isEqualByComparingTo("4");
        assertThat(response.items().get(0).reviewScore()).isEqualTo("4.5");
    }

    @Test
    void regionId는_공급자가_준_정수_그대로라_0이나_음수_long_최댓값도_받고_long_범위_밖은_400이다() {
        MyRealTripRateLimiter roomy = new MyRealTripRateLimiter(10, Duration.ofMinutes(1), Clock.systemUTC());
        MyRealTripProductService svc = new MyRealTripProductService(partner, roomy, Clock.fixed(NOW_UTC, ZoneOffset.UTC));
        svc.searchHotels(userId, "0", "2026-12-10", "2026-12-12", "1", null);
        svc.searchHotels(userId, "-7", "2026-12-10", "2026-12-12", "1", null);
        svc.searchHotels(userId, "9223372036854775807", "2026-12-10", "2026-12-12", "1", null);
        assertThat(partner.hotelQueries).extracting(HotelSearchQuery::regionId)
                .containsExactly(0L, -7L, Long.MAX_VALUE);
        assertInvalid(() -> svc.searchHotels(userId, "9223372036854775808", "2026-12-10", "2026-12-12", "1", null));
    }

    @Test
    void 숙소_조회의_page는_선택이고_기본_1이다() {
        service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "1", null);
        service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "1", " ");
        assertThat(partner.hotelQueries).extracting(HotelSearchQuery::page).containsExactly(1, 1);
    }

    @Test
    void 과거_체크인은_서울_기준일로_판정한다() {
        // UTC 로는 아직 11-05 이지만 서울은 11-06 — 11-05 체크인은 과거다.
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-11-05", "2026-11-07", "1", null));
        // 서울 오늘(11-06)은 허용.
        service.searchHotels(userId, "1", "2026-11-06", "2026-11-07", "1", null);
        assertThat(partner.hotelQueries).hasSize(1);
    }

    @Test
    void 숙소_조회_입력_오류는_400이고_파트너를_부르지_않는다() {
        assertInvalid(() -> service.searchHotels(userId, null, "2026-12-10", "2026-12-12", "1", null));      // regionId 누락
        assertInvalid(() -> service.searchHotels(userId, "abc", "2026-12-10", "2026-12-12", "1", null));     // regionId 문자열
        assertInvalid(() -> service.searchHotels(userId, "+1", "2026-12-10", "2026-12-12", "1", null));      // 부호 +
        assertInvalid(() -> service.searchHotels(userId, "１", "2026-12-10", "2026-12-12", "1", null));       // 전각 숫자
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "２", null));       // 전각 인원
        assertInvalid(() -> service.searchHotels(userId, "1", "2026/12/10", "2026-12-12", "1", null));       // 날짜 형식
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-02-30", "2026-03-01", "1", null));       // 없는 날짜
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-12", "2026-12-10", "1", null));       // 역전
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-10", "1", null));       // 같은 날
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", null, "1", null));               // checkOut 누락
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "0", null));       // 성인 0
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "1.5", null));     // 소수
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "-2", null));      // 음수
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", null, null));      // 성인 누락
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "1", "0"));        // page 0
        assertInvalid(() -> service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "1", "two"));      // page 문자열
        assertThat(partner.hotelQueries).isEmpty();
    }

    @Test
    void 투어_티켓_검색은_키워드_공백을_지우고_page_기본_1로_포트에_넘긴다() {
        partner.page = ProductPage.of(List.of(product("티켓 A")), 1, null, 20);

        ProductPageResponse<TicketSearchResponse.Item> response = service.searchTickets(userId, " 도쿄 ", null);
        service.searchTickets(userId, "오사카", "3");

        assertThat(partner.ticketCalls).containsExactly("도쿄|1", "오사카|3");
        assertThat(response.totalCount()).isNull();
        assertThat(response.hasMore()).isTrue();
        assertThat(response.items().get(0).description()).isEqualTo("설명");
        assertInvalid(() -> service.searchTickets(userId, "  ", null));
        assertInvalid(() -> service.searchTickets(userId, "도쿄", "0"));
    }

    @Test
    void 숙소와_티켓이_자동완성과_같은_사용자_한도를_공유하고_초과_시_파트너를_부르지_않는다() {
        service.searchTickets(userId, "도쿄", null);
        service.searchHotels(userId, "1", "2026-12-10", "2026-12-12", "1", null);
        assertThatThrownBy(() -> service.searchTickets(userId, "도쿄", null))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(partner.ticketCalls).hasSize(1);
        assertThat(partner.hotelQueries).hasSize(1);
    }

    @Test
    void 인증_주체가_없으면_401이다() {
        assertThatThrownBy(() -> service.searchTickets(null, "도쿄", null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    private static Product product(String name) {
        return new Product(name, "https://myrealtrip.com/p/1", new BigDecimal("120000"), null, "설명",
                new BigDecimal("4"), "4.5", 120);
    }

    private static void assertInvalid(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_MYREALTRIP_REQUEST);
    }

    private static final class FakePartnerClient implements MyRealTripPartnerClient {
        ProductPage page = ProductPage.of(List.of(), 1, 0L, 0);
        final List<HotelSearchQuery> hotelQueries = new ArrayList<>();
        final List<String> ticketCalls = new ArrayList<>();

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
            hotelQueries.add(query);
            return page;
        }

        @Override
        public ProductPage searchTickets(String keyword, int page) {
            ticketCalls.add(keyword + "|" + page);
            return this.page;
        }
    }
}
