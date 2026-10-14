package com.soma.yeolo.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 여행 코스 생성 요청 (API-COURSE-1).
 *
 * <p>명세 개정(2026-10)으로 예산은 유형({@code budgetType}: cost_effective|moderate|luxury)이 아니라
 * <b>금액({@code budget}: number)</b>으로 받는다. 옛 필드 {@code budgetType}만 보내면 미지 필드로
 * 무시되어 {@code budget}이 비므로 400이 된다 — 명세에서 제거된 값은 BE 경계에서 끊는다.
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

        /**
         * 총 예산 금액. 명세가 {@code number}라 소수도 받는다({@code Long}으로 받으면 Jackson이
         * 1500.75를 조용히 1500으로 잘라 버린다). 통화는 명세에 없어 FE·AI가 같은 단위를 쓴다고
         * 보고 BE는 그대로 중계한다.
         */
        @NotNull(message = "여행 조건 입력값이 올바르지 않습니다.")
        @Positive(message = "여행 조건 입력값이 올바르지 않습니다.")
        BigDecimal budget
) {
}
