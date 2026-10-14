package com.soma.yeolo.course.client.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.course.domain.TripCondition;
import com.soma.yeolo.preference.domain.Mbti;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * AI 코스 생성 요청 본문 (API-AI-2). 예산은 명세 개정(2026-10)으로 {@code budget}(금액)이다.
 * {@code tasteProfile}은 저장된 원본 JSON 트리를 그대로 실어 보낸다.
 */
public record AiCourseGenerationRequest(
        String userId,
        String mbti,
        JsonNode tasteProfile,
        TripConditionPayload tripCondition
) {

    public record TripConditionPayload(
            String destinationCountry,
            String destinationCity,
            String startDate,
            int totalDays,
            BigDecimal budget
    ) {
    }

    public static AiCourseGenerationRequest of(UUID userId, Mbti mbti, JsonNode tasteProfile,
                                               TripCondition condition) {
        return new AiCourseGenerationRequest(
                userId.toString(),
                mbti == null ? null : mbti.name(),
                tasteProfile,
                new TripConditionPayload(
                        condition.destinationCountry(),
                        condition.destinationCity(),
                        condition.startDate().toString(),
                        condition.totalDays(),
                        condition.budget()
                )
        );
    }
}
