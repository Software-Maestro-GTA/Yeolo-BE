package com.soma.yeolo.tasteprofile.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 촬영 계절 구분. 기준 월은 DOM-5 §5 표를 그대로 따른다.
 *
 * <pre>
 * spring 3~5월 / summer 6~8월 / autumn 9~11월 / winter 12~2월
 * </pre>
 */
@Getter
@RequiredArgsConstructor
public enum Season {

    SPRING("spring"),
    SUMMER("summer"),
    AUTUMN("autumn"),
    WINTER("winter");

    private final String value;

    /** 1~12 월(month of year)을 계절로 변환한다. */
    public static Season fromMonth(int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be 1..12: " + month);
        }
        return switch (month) {
            case 3, 4, 5 -> SPRING;
            case 6, 7, 8 -> SUMMER;
            case 9, 10, 11 -> AUTUMN;
            default -> WINTER; // 12, 1, 2
        };
    }
}
