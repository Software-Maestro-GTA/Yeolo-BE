package com.soma.yeolo.course.client.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.course.domain.TripCondition;
import java.util.UUID;

/**
 * BE → AI 코스 생성 요청 (API-BA-1 Request Body). 전송 스키마의 필드명을 명세 그대로 사용한다.
 *
 * <p>성향 프로필은 저장된 AI 원본 {@code tasteProfile} JSON을 그대로 전달하고, 여행 조건은
 * 정규화된 {@link TripCondition}을 명세 전송값으로 매핑한다.
 *
 * @param userId        코스 소유자 식별자
 * @param tasteProfile  성향 프로필 원본 JSON (API-BA-1 {@code tasteProfile})
 * @param tripCondition 여행 조건 (지역/날짜/예산)
 */
public record AiCourseGenerationRequest(
        String userId,
        JsonNode tasteProfile,
        TripConditionPayload tripCondition
) {

    /** API-BA-1 {@code tripCondition} 전송 스키마. */
    public record TripConditionPayload(
            String destinationCountry,
            String destinationCity,
            String startDate,
            int totalDays,
            String budgetType
    ) {
    }

    /** 소유자·성향 프로필 JSON·정규화된 여행 조건으로 AI 요청 본문을 구성한다. */
    public static AiCourseGenerationRequest of(UUID userId, JsonNode tasteProfile, TripCondition condition) {
        return new AiCourseGenerationRequest(
                userId.toString(),
                tasteProfile,
                new TripConditionPayload(
                        condition.destinationCountry(),
                        condition.destinationCity(),
                        condition.startDate().toString(),
                        condition.totalDays(),
                        condition.budgetType().getValue()
                )
        );
    }
}
