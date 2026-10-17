package com.soma.yeolo.course.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 검증·파싱이 끝난 여행 조건 (FUN-6). 예산은 명세 개정(2026-10)으로 유형이 아니라 금액이다.
 *
 * <p>명세(API-COURSE-1·API-AI-2)가 {@code budget: number}라 소수도 올 수 있어 {@link BigDecimal}로
 * 받는다. 양수가 아니면 도메인 경계에서 거부한다 — 컨트롤러 검증만 믿으면 다른 호출자가 0이나
 * 음수를 AI로 흘려보내 400이 되고, 그 실패는 사용자에게 500으로 보인다.
 *
 * @param budget 총 예산 금액 (양수). 단위·통화는 FE·AI 간 약속을 그대로 따른다.
 */
public record TripCondition(
        String destinationCountry,
        String destinationCity,
        LocalDate startDate,
        int totalDays,
        BigDecimal budget
) {

    public TripCondition {
        if (budget == null || budget.signum() <= 0) {
            throw new IllegalArgumentException("budget must be positive: " + budget);
        }
    }
}
