package com.soma.yeolo.tasteprofile.dto;

/**
 * 내 성향 프로필 조회 응답의 {@code data} 페이로드 (API-FB-8).
 *
 * <p>{@code tasteProfile}은 저장된 AI 원본 성향 JSON을 {@link TasteProfilePayload} 타입으로 역직렬화하고
 * 권위 식별자·갱신일을 덮어쓴 값으로, 명세 §3의 tasteProfile 스키마를 그대로 전달한다.
 */
public record MyTasteProfileResponse(TasteProfilePayload tasteProfile) {
}
