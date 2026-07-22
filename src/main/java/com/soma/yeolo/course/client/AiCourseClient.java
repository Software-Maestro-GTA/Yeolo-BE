package com.soma.yeolo.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.course.client.dto.AiCourseGenerationRequest;

/**
 * 성향 프로필·여행 조건 → AI 코스 생성 포트 (API-BA-1). 서비스(응용 계층)가 소유하는 아웃바운드
 * 인터페이스로, AI 내부 API 호출 세부는 알지 못한다(DIP, docs/architecture.md §5).
 *
 * <p>구현체 교체만으로 실 provider를 붙일 수 있도록 한다. 기본은 {@code StubAiCourseClient}이며,
 * 실제 {@code /internal/ai/courses}(SSE) 연동 어댑터는 TSK-7(#4)에서 제공한다.
 */
public interface AiCourseClient {

    /**
     * 성향 프로필과 정규화된 여행 조건으로 AI 코스 생성을 요청하고, 생성된 {@code course} JSON을 반환한다.
     *
     * @return AI가 산출한 {@code course} JSON 노드 (title/itinerary 등 포함)
     * @throws com.soma.yeolo.global.exception.BusinessException 생성 실패 시 (AI_COURSE_GENERATION_ERROR)
     */
    JsonNode generateCourse(AiCourseGenerationRequest request);
}
