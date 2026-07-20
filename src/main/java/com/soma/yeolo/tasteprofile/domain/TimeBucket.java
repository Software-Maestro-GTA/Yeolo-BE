package com.soma.yeolo.tasteprofile.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 촬영 시간대 구분. 경계값은 DOM-5 §5 표를 그대로 따른다.
 *
 * <pre>
 * dawn      05:00 이전
 * morning   05:00 ~ 11:59
 * afternoon 12:00 ~ 16:59
 * evening   17:00 ~ 20:59
 * night     21:00 이후
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public enum TimeBucket {

    DAWN("dawn"),
    MORNING("morning"),
    AFTERNOON("afternoon"),
    EVENING("evening"),
    NIGHT("night");

    private final String value;

    /** 0~23 시(hour of day)를 시간대로 변환한다. */
    public static TimeBucket fromHour(int hour) {
        if (hour < 0 || hour > 23) {
            throw new IllegalArgumentException("hour must be 0..23: " + hour);
        }
        if (hour < 5) {
            return DAWN;
        }
        if (hour < 12) {
            return MORNING;
        }
        if (hour < 17) {
            return AFTERNOON;
        }
        if (hour < 21) {
            return EVENING;
        }
        return NIGHT;
    }
}
