package com.soma.yeolo.myrealtrip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.myrealtrip.domain.Coupon;
import com.soma.yeolo.myrealtrip.dto.CouponListResponse;
import com.soma.yeolo.myrealtrip.service.port.CouponRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 쿠폰 목록 — 기간 필터(활성은 저장소가 거르지만 도메인 규칙으로 한 번 더), 저장된 표시 순서 유지, 서울 기준일, 401, 한도. 시계는 UTC 2026-11-05T16:00Z = 서울 11-06 01:00.
 */
class MyRealTripCouponServiceTest {

    private static final Instant NOW_UTC = Instant.parse("2026-11-05T16:00:00Z");
    private static final LocalDate SEOUL_TODAY = LocalDate.parse("2026-11-06");

    private final UUID userId = UUID.randomUUID();

    @Test
    void 활성이고_기간_안인_쿠폰만_저장된_순서대로_code_minimumPrice_discount로_돌려준다() {
        MyRealTripCouponService service = service(List.of(
                new Coupon("PACKMKTP5000", 150000, 5000, true, null, null),
                new Coupon("PACKMKTP1000", 50000, 1000, true, SEOUL_TODAY, SEOUL_TODAY),     // 오늘 하루만 — 포함
                new Coupon("EXPIRED", 1, 1, true, null, SEOUL_TODAY.minusDays(1)),           // 어제 종료
                new Coupon("NOT_YET", 1, 1, true, SEOUL_TODAY.plusDays(1), null),            // 내일 시작
                new Coupon("OFF", 1, 1, false, null, null),                                  // 비활성
                new Coupon("PACKMKTP3000", 100000, 3000, true, null, null)));

        CouponListResponse response = service.list(userId);

        // 저장소가 준 순서(표시 순서)를 바꾸지 않는다 — 정렬은 저장소 책임이다.
        assertThat(response.coupons()).containsExactly(
                new CouponListResponse.Entry("PACKMKTP5000", 150000, 5000),
                new CouponListResponse.Entry("PACKMKTP1000", 50000, 1000),
                new CouponListResponse.Entry("PACKMKTP3000", 100000, 3000));
    }

    @Test
    void 기준일은_서울_날짜다() {
        // UTC 로는 아직 11-05 — UTC 로 재면 11-05 종료 쿠폰이 보이고 11-06 시작 쿠폰이 숨는다.
        MyRealTripCouponService service = service(List.of(
                new Coupon("ENDED_YESTERDAY_SEOUL", 1, 1, true, null, LocalDate.parse("2026-11-05")),
                new Coupon("STARTS_TODAY_SEOUL", 1, 1, true, LocalDate.parse("2026-11-06"), null)));

        assertThat(service.list(userId).coupons()).extracting(CouponListResponse.Entry::code)
                .containsExactly("STARTS_TODAY_SEOUL");
    }

    @Test
    void 노출할_쿠폰이_없으면_빈_배열이다() {
        assertThat(service(List.of()).list(userId).coupons()).isEmpty();
        assertThat(service(List.of(new Coupon("OFF", 1, 1, false, null, null))).list(userId).coupons()).isEmpty();
    }

    @Test
    void 인증_주체가_없으면_401이고_저장소를_읽지_않는다() {
        boolean[] read = {false};
        MyRealTripCouponService service = new MyRealTripCouponService(() -> {
            read[0] = true;
            return List.of();
        }, limiter(60), Clock.fixed(NOW_UTC, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.list(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(read[0]).isFalse();
    }

    @Test
    void 다른_MRT_API와_같은_사용자_한도를_쓰고_초과_시_저장소를_읽지_않는다() {
        int[] reads = {0};
        CouponRepository repository = () -> {
            reads[0]++;
            return List.of();
        };
        MyRealTripCouponService service = new MyRealTripCouponService(repository, limiter(1),
                Clock.fixed(NOW_UTC, ZoneOffset.UTC));

        service.list(userId);
        assertThatThrownBy(() -> service.list(userId)).isInstanceOf(RateLimitExceededException.class);
        assertThat(reads[0]).isEqualTo(1);
    }

    private static MyRealTripRateLimiter limiter(int max) {
        return new MyRealTripRateLimiter(max, Duration.ofMinutes(1), Clock.systemUTC());
    }

    private static MyRealTripCouponService service(List<Coupon> stored) {
        CouponRepository repository = () -> stored;
        return new MyRealTripCouponService(repository, limiter(60), Clock.fixed(NOW_UTC, ZoneOffset.UTC));
    }
}
