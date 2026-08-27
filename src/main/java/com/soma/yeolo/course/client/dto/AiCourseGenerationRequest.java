package com.soma.yeolo.course.client.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.course.domain.TripCondition;
import com.soma.yeolo.preference.domain.Mbti;
import java.util.UUID;

/**
 * BE → AI 코스 생성 요청 (API-AI-2 Request Body). 전송 스키마의 필드명·순서를 명세 그대로 사용한다.
 *
 * <p>{@code mbti}와 {@code tasteProfile}은 각각 null일 수 있다 — DOM-3상 둘 중 하나만 있어도 코스를
 * 생성하며, 둘 다 있으면 AI가 함께 반영한다. 성향 프로필은 저장된 AI 원본 JSON을 그대로 전달하고,
 * 여행 조건은 정규화된 {@link TripCondition}을 명세 전송값으로 매핑한다.
 *
 * @param userId        코스 소유자 식별자
 * @param mbti          사용자 MBTI 4글자 (API-PREF-1로 저장된 값, 미입력 시 null)
 * @param tasteProfile  성향 프로필 원본 JSON (미분석 시 null)
 * @param tripCondition 여행 조건 (지역/날짜/예산)
 */
public record AiCourseGenerationRequest(
        String userId,
        String mbti,
        JsonNode tasteProfile,
        TripConditionPayload tripCondition
) {

    /** API-AI-2 {@code tripCondition} 전송 스키마. */
    public record TripConditionPayload(
            String destinationCountry,
            String destinationCity,
            String startDate,
            int totalDays,
            String budgetType
    ) {
    }

    /** 소유자·MBTI·성향 프로필 JSON·정규화된 여행 조건으로 AI 요청 본문을 구성한다. */
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
                        condition.budgetType().getValue()
                )
        );
    }
}
