package com.soma.yeolo.myrealtrip.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 노출 규칙 — 활성 + 적용 기간(날짜, 양 끝 포함, 비면 무제한). */
class CouponTest {

    private static final LocalDate D10 = LocalDate.parse("2026-11-10");
    private static final LocalDate D20 = LocalDate.parse("2026-11-20");

    @Test
    void 활성이고_기간_안이면_노출하며_양_끝_날짜를_포함한다() {
        Coupon coupon = new Coupon("C", 50000, 1000, true, D10, D20);
        assertThat(coupon.isAvailableOn(D10)).isTrue();
        assertThat(coupon.isAvailableOn(D20)).isTrue();
        assertThat(coupon.isAvailableOn(D10.minusDays(1))).isFalse();
        assertThat(coupon.isAvailableOn(D20.plusDays(1))).isFalse();
    }

    @Test
    void 기간이_비면_그쪽_제한이_없고_비활성이면_기간_안이라도_숨긴다() {
        assertThat(new Coupon("C", 0, 0, true, null, null).isAvailableOn(D10)).isTrue();
        assertThat(new Coupon("C", 0, 0, true, D10, null).isAvailableOn(LocalDate.MAX)).isTrue();
        assertThat(new Coupon("C", 0, 0, true, null, D20).isAvailableOn(LocalDate.MIN)).isTrue();
        assertThat(new Coupon("C", 0, 0, false, D10, D20).isAvailableOn(D10)).isFalse();
    }

    @Test
    void 앞뒤_공백_탭_개행이_붙은_코드는_정리하지_않고_거절한다() {
        assertThatThrownBy(() -> new Coupon(" PACKMKTP1000", 1, 1, true, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coupon("PACKMKTP1000\t", 1, 1, true, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coupon("PACKMKTP1000\n", 1, 1, true, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 빈_코드_음수_금액_역전된_기간은_만들_수_없다() {
        assertThatThrownBy(() -> new Coupon(" ", 1, 1, true, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coupon("C", -1, 1, true, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coupon("C", 1, -1, true, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coupon("C", 1, 1, true, D20, D10)).isInstanceOf(IllegalArgumentException.class);
    }
}
