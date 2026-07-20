package com.soma.yeolo.tasteprofile.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 저장된 성향 프로필 조회 결과 (API-FB-8). 신규 저장용 {@link TasteProfile}과 달리 부여된
 * 식별자·갱신 시각 등 영속 이후의 권위 값을 함께 담는 읽기 전용 모델이다.
 *
 * <p>세부 점수 지표 전체는 {@code profileJson}(AI 원본 tasteProfile JSON)에 보존되어 있으며,
 * 응답 조립 시 {@code id}/{@code userId}/{@code sourceType}/{@code updatedAt}를 권위 값으로 덮어쓴다.
 *
 * @param id          성향 프로필 식별자
 * @param userId      소유자 식별자
 * @param sourceType  성향 생성 방식
 * @param updatedAt   마지막 갱신 시각 (UTC)
 * @param profileJson AI tasteProfile 원본 JSON 전체
 */
public record SavedTasteProfile(
        UUID id,
        UUID userId,
        SourceType sourceType,
        Instant updatedAt,
        String profileJson
) {
}
