package com.soma.yeolo.course.domain;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 예산 성향 (API-COURSE-1 / API-AI-2 {@code budgetType}). 전송·저장값은 명세의 소문자 값을 그대로 쓴다.
 * 사용자가 입력한 예산 조건을 추천 알고리즘이 사용할 정규화 값으로 다룬다. (FUN-6)
 *
 * <p>명세 개정으로 {@code standard} → {@code moderate}로 값이 변경되었다. 더 이상 지원하지 않는
 * {@code standard} 요청은 {@link #fromValue(String)}에서 예외로 거부된다. (DOM-3 예산 타입)
 */
@Getter
@RequiredArgsConstructor
public enum BudgetType {

    COST_EFFECTIVE("cost_effective"),
    MODERATE("moderate"),
    LUXURY("luxury");

    private final String value;

    /** 명세 전송값 → enum. 알 수 없는 값이면 {@link IllegalArgumentException}. */
    public static BudgetType fromValue(String value) {
        return Arrays.stream(values())
                .filter(b -> b.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown budget type: " + value));
    }
}
