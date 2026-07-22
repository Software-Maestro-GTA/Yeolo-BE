package com.soma.yeolo.course.domain;

import java.time.LocalDate;

/**
 * 정규화된 여행 조건 (FUN-6). 사용자가 입력한 지역·날짜·예산을 추천 알고리즘(AI, API-BA-1)이
 * 사용할 수 있는 형태로 변환한 순수 도메인 값이다. 원시 요청 문자열이 아닌 검증·파싱된 값을 담는다.
 *
 * @param destinationCountry 여행 국가
 * @param destinationCity    여행 도시/지역
 * @param startDate          여행 시작일
 * @param totalDays          총 여행 일수 (1 이상)
 * @param budgetType         예산 성향
 */
public record TripCondition(
        String destinationCountry,
        String destinationCity,
        LocalDate startDate,
        int totalDays,
        BudgetType budgetType
) {
}
