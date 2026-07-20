package com.soma.yeolo.tasteprofile.domain;

import java.util.List;
import java.util.UUID;

/**
 * 성향 프로필 분석 결과 (DOM-1). AI가 산출한 정규화 성향을 저장하기 위한 순수 도메인 모델이며,
 * 세부 점수 지표 전체는 {@code profileJson}(AI 원본 tasteProfile JSON)으로 보존하고,
 * 목록 필터·추천 분기에 쓰이는 상위 분류는 별도 필드로 승격한다(DOM-1 §6).
 *
 * @param userId                        소유자 식별자
 * @param sourceType                    성향 생성 방식 (behavior 등)
 * @param profileJson                   AI tasteProfile 원본 JSON 전체
 * @param travelPaceDensity             여행 속도/일정 밀도 (DOM-1 값, 없으면 null)
 * @param spendingTendency              소비 성향 (DOM-1 값, 없으면 null)
 * @param companionType                 동행 형태 (DOM-1 값, 없으면 null)
 * @param seasonalEnvironmentPreference 계절·환경 취향 (택 N)
 */
public record TasteProfile(
        UUID userId,
        SourceType sourceType,
        String profileJson,
        String travelPaceDensity,
        String spendingTendency,
        String companionType,
        List<String> seasonalEnvironmentPreference
) {

    public TasteProfile {
        seasonalEnvironmentPreference =
                seasonalEnvironmentPreference == null ? List.of() : List.copyOf(seasonalEnvironmentPreference);
    }
}
