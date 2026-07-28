package com.soma.yeolo.tasteprofile.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 성향 프로필 조회 응답 페이로드 (API-FB-8 / DOM-1). FE가 타입으로 소비할 수 있도록 명세의
 * tasteProfile 스키마(travelPurpose 등 세부 지표 포함)를 그대로 필드로 노출한다.
 *
 * <p>저장된 AI 원본 tasteProfile JSON을 이 타입으로 역직렬화하고, 영속 이후 확정된 권위 값
 * ({@code tasteProfileId}·{@code userId}·{@code sourceType}·{@code updatedAt})만 덮어써 반환한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TasteProfilePayload(
        String tasteProfileId,
        String userId,
        String sourceType,
        String updatedAt,
        TravelPurpose travelPurpose,
        String travelPaceDensity,
        PreferredLocationType preferredLocationType,
        ActivityPreference activityPreference,
        String spendingTendency,
        String companionType,
        FoodPreference foodPreference,
        List<String> seasonalEnvironmentPreference
) {

    /** 파싱된 원본 지표는 유지하고 권위 값(식별자·생성 방식·갱신일)만 교체한 새 페이로드를 만든다. */
    public TasteProfilePayload withAuthority(
            String tasteProfileId, String userId, String sourceType, String updatedAt) {
        return new TasteProfilePayload(
                tasteProfileId, userId, sourceType, updatedAt,
                travelPurpose, travelPaceDensity, preferredLocationType, activityPreference,
                spendingTendency, companionType, foodPreference, seasonalEnvironmentPreference);
    }

    /** 여행 목적 선호도 (각 1~5). */
    public record TravelPurpose(
            Integer relaxation,
            Integer sightseeing,
            Integer culturalExperience,
            Integer gourmet,
            Integer natureExploration,
            Integer activity,
            Integer shopping,
            Integer festivalEvent,
            Integer wellness,
            Integer selfDevelopment
    ) {
    }

    /** 선호 장소 유형 (각 1~5). */
    public record PreferredLocationType(
            Integer bigCity,
            Integer smallTownAlley,
            Integer natureHinterland,
            Integer beachResort,
            Integer mountainPlateau,
            Integer historicalCity,
            Integer themeParkResort,
            Integer famousSpotPreferred,
            Integer hiddenSpotPreferred
    ) {
    }

    /** 활동 취향 (각 1~5). */
    public record ActivityPreference(
            Integer viewing,
            Integer experience,
            Integer adventure,
            Integer photographyVideo,
            Integer gourmetExploration,
            Integer nightlife,
            Integer shopping,
            Integer relaxation,
            Integer localInteraction
    ) {
    }

    /** 음식 취향 (각 1~5). */
    public record FoodPreference(
            Integer localFoodActive,
            Integer famousRestaurantCentered,
            Integer streetFood,
            Integer cafeDessert,
            Integer fineDining,
            Integer familiarFoodPreferred,
            Integer dietaryRestriction,
            Integer sightseeingOverFood
    ) {
    }
}
