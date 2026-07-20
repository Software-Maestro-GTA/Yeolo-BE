package com.soma.yeolo.tasteprofile.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 촬영 요일. DB/AI 전송값은 DOM-5 명세의 소문자 3글자를 그대로 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum Weekday {

    MON("mon"),
    TUE("tue"),
    WED("wed"),
    THU("thu"),
    FRI("fri"),
    SAT("sat"),
    SUN("sun");

    private final String value;

    /** {@link java.time.DayOfWeek}(MONDAY..SUNDAY) → DOM-5 요일 값. */
    public static Weekday from(java.time.DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> MON;
            case TUESDAY -> TUE;
            case WEDNESDAY -> WED;
            case THURSDAY -> THU;
            case FRIDAY -> FRI;
            case SATURDAY -> SAT;
            case SUNDAY -> SUN;
        };
    }

    public boolean isWeekend() {
        return this == SAT || this == SUN;
    }
}
