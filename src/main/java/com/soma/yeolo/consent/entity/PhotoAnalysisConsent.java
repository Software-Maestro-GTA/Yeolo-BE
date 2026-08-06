package com.soma.yeolo.consent.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사진 데이터 분석 동의 이력 (API-PREF-2 / FUN-3 / REQ-8).
 *
 * <p><b>append-only 이력</b>이다. 동의·철회가 발생할 때마다 새 행을 쌓고 기존 행은 수정하지 않는다.
 * 법적 증빙 목적상 "언제 어떤 버전의 동의서에 동의/철회했는지"가 모두 남아야 하기 때문이다.
 * 현재 동의 여부는 {@code user_id} 기준 가장 최근 {@code agreed_at} 행으로 판정한다.
 *
 * <p>식별·기록 위주이고 보호할 상태 전이 규칙이 없으므로 "엔티티=도메인" 병합형을 택했다
 * (docs/architecture.md 1-1).
 */
@Getter
@Entity
@Table(name = "photo_analysis_consents", indexes = {
        @Index(name = "idx_photo_analysis_consents_user_id_agreed_at",
                columnList = "user_id, agreed_at DESC")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PhotoAnalysisConsent extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    /** 동의 여부. 철회는 별도 API가 아니라 {@code agreed=false} 행으로 기록한다. */
    @Column(name = "agreed", updatable = false, nullable = false)
    private boolean agreed;

    /** 사용자가 동의한 동의서 버전. 동의서 개정 시 재동의 필요 여부 판단 근거가 된다. */
    @Column(name = "consent_version", updatable = false, nullable = false)
    private String consentVersion;

    /** 동의/철회 시각. 클라이언트 값이 아닌 서버 시각으로 기록한다(증빙 신뢰성). */
    @Column(name = "agreed_at", updatable = false, nullable = false)
    private Instant agreedAt;

    private PhotoAnalysisConsent(UUID userId, boolean agreed, String consentVersion, Instant agreedAt) {
        this.userId = userId;
        this.agreed = agreed;
        this.consentVersion = consentVersion;
        this.agreedAt = agreedAt;
    }

    /** 동의(또는 철회) 한 건을 이력으로 기록한다. */
    public static PhotoAnalysisConsent record(UUID userId, boolean agreed, String consentVersion,
                                              Instant agreedAt) {
        return new PhotoAnalysisConsent(userId, agreed, consentVersion, agreedAt);
    }
}
