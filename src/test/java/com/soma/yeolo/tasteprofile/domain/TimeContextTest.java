package com.soma.yeolo.tasteprofile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TimeContextTest {

    @Test
    void capturedAt과_타임존으로_요일_시간대_계절을_파생한다() {
        // 2026-07-14는 화요일, 10시(morning), 7월(summer), 평일
        TimeContext context = TimeContext.derive("2026-07-14T10:00:00+09:00", "Asia/Seoul");

        assertThat(context.dayOfWeek()).isEqualTo(Weekday.TUE);
        assertThat(context.isWeekend()).isFalse();
        assertThat(context.timeBucket()).isEqualTo(TimeBucket.MORNING);
        assertThat(context.season()).isEqualTo(Season.SUMMER);
        assertThat(context.capturedAt()).isEqualTo("2026-07-14T10:00:00+09:00");
    }

    @Test
    void 주말이면_isWeekend가_참이다() {
        // 2026-07-18은 토요일
        TimeContext context = TimeContext.derive("2026-07-18T22:00:00+09:00", "Asia/Seoul");

        assertThat(context.dayOfWeek()).isEqualTo(Weekday.SAT);
        assertThat(context.isWeekend()).isTrue();
        assertThat(context.timeBucket()).isEqualTo(TimeBucket.NIGHT);
    }

    @Test
    void 타임존_기준으로_지역시각을_계산해_요일과_시간대가_바뀔_수_있다() {
        // UTC 2026-07-14T22:00Z = Asia/Seoul 2026-07-15T07:00 (수요일 아침)
        TimeContext context = TimeContext.derive("2026-07-14T22:00:00Z", "Asia/Seoul");

        assertThat(context.dayOfWeek()).isEqualTo(Weekday.WED);
        assertThat(context.timeBucket()).isEqualTo(TimeBucket.MORNING);
    }

    @Test
    void 타임존이_없으면_capturedAt_오프셋을_기준으로_계산한다() {
        TimeContext context = TimeContext.derive("2026-07-14T10:00:00+09:00", null);

        assertThat(context.dayOfWeek()).isEqualTo(Weekday.TUE);
        assertThat(context.timeBucket()).isEqualTo(TimeBucket.MORNING);
    }

    @Test
    void 유효하지_않은_타임존이면_오프셋으로_폴백한다() {
        TimeContext context = TimeContext.derive("2026-07-14T10:00:00+09:00", "Not/AZone");

        assertThat(context.timeBucket()).isEqualTo(TimeBucket.MORNING);
        assertThat(context.dayOfWeek()).isEqualTo(Weekday.TUE);
    }

    @Test
    void capturedAt이_ISO8601이_아니면_예외를_던진다() {
        assertThatThrownBy(() -> TimeContext.derive("2026/07/14 10:00", "Asia/Seoul"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
