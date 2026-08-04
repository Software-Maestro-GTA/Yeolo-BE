package com.soma.yeolo.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 코스 생성 요청 (API-FB-4 Request Body). 지역·날짜·예산 조건을 입력받는다. (FUN-6)
 *
 * <p>형식 검증은 Bean Validation으로 스트림 시작 전에 수행하며, 위반 시 명세의 400 응답
 * ("여행 조건 입력값이 올바르지 않습니다.")으로 반환한다. 값의 정규화(날짜 파싱·예산 매핑)는
 * 서비스에서 {@code TripCondition}으로 변환하며 담당한다.
 *
 * @param destinationCountry 여행 국가 (필수)
 * @param destinationCity    여행 도시/지역 (필수)
 * @param startDate          여행 시작일 (YYYY-MM-DD)
 * @param totalDays          총 여행 일수 (1 이상)
 * @param budgetType         예산 성향 — {@code cost_effective | standard | luxury}
 */
public record CourseCreationRequest(
        @NotBlank(message = "여행 조건 입력값이 올바르지 않습니다.")
        String destinationCountry,

        @NotBlank(message = "여행 조건 입력값이 올바르지 않습니다.")
        String destinationCity,

        @NotBlank(message = "여행 조건 입력값이 올바르지 않습니다.")
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "여행 조건 입력값이 올바르지 않습니다.")
        String startDate,

        @NotNull(message = "여행 조건 입력값이 올바르지 않습니다.")
        @Positive(message = "여행 조건 입력값이 올바르지 않습니다.")
        Integer totalDays,

        @NotBlank(message = "여행 조건 입력값이 올바르지 않습니다.")
        @Pattern(regexp = "cost_effective|standard|luxury", message = "여행 조건 입력값이 올바르지 않습니다.")
        String budgetType
) {
}
