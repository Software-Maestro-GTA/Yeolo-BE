package com.soma.yeolo.tasteprofile.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 내 성향 프로필 조회 응답의 {@code data} 페이로드 (API-FB-8).
 *
 * <p>{@code tasteProfile}은 저장된 AI 원본 성향 JSON에 권위 식별자·갱신일을 덮어쓴 노드로,
 * 명세 §3의 tasteProfile 스키마(travelPurpose 등 세부 지표 포함)를 그대로 전달한다.
 */
public record MyTasteProfileResponse(JsonNode tasteProfile) {
}
