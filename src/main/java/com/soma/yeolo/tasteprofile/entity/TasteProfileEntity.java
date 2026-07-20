package com.soma.yeolo.tasteprofile.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 성향 프로필 (DOM-1). 세부 점수 지표 전체는 {@code profile}(AI 원본 JSON)로 보존하고,
 * 목록 필터·추천 분기에 쓰이는 상위 분류는 별도 컬럼으로 저장한다. (DOM-1 §6)
 */
@Getter
@Entity
@Table(name = "taste_profiles")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TasteProfileEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Lob
    @Column(name = "profile", nullable = false)
    private String profile;

    @Column(name = "travel_pace_density")
    private String travelPaceDensity;

    @Column(name = "spending_tendency")
    private String spendingTendency;

    @Column(name = "companion_type")
    private String companionType;

    @Lob
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "seasonal_environment_preference")
    private List<String> seasonalEnvironmentPreference;

    @Builder
    private TasteProfileEntity(UUID userId, SourceType sourceType, String profile,
                              String travelPaceDensity, String spendingTendency, String companionType,
                              List<String> seasonalEnvironmentPreference) {
        this.userId = userId;
        this.sourceType = sourceType;
        this.profile = profile;
        this.travelPaceDensity = travelPaceDensity;
        this.spendingTendency = spendingTendency;
        this.companionType = companionType;
        this.seasonalEnvironmentPreference = seasonalEnvironmentPreference;
    }

    /** 순수 도메인 → 영속 엔티티 매핑. */
    public static TasteProfileEntity from(TasteProfile profile) {
        return TasteProfileEntity.builder()
                .userId(profile.userId())
                .sourceType(profile.sourceType())
                .profile(profile.profileJson())
                .travelPaceDensity(profile.travelPaceDensity())
                .spendingTendency(profile.spendingTendency())
                .companionType(profile.companionType())
                .seasonalEnvironmentPreference(profile.seasonalEnvironmentPreference())
                .build();
    }
}
