package com.soma.yeolo.preference.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.preference.domain.Mbti;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 선호 입력값 (API-PREF-1 / DOM-1).
 *
 * <p>DOM-1은 "MBTI는 사용자 선호 입력값으로 별도 관리되며, 사용자 정보 자체의 필드로 저장하지
 * 않는다"고 규정한다. 그래서 {@code users}가 아니라 사용자당 1행인 별도 테이블로 둔다
 * ({@code user_id} 유니크). 선호 항목이 늘어나면 컬럼만 추가하면 된다.
 *
 * <p>식별·저장 위주이고 보호할 상태 전이 규칙이 없어 "엔티티=도메인" 병합형을 택했다
 * (docs/architecture.md §1-1 — {@code PhotoAnalysisConsent}와 같은 판단). 유효성은 폐집합
 * {@link Mbti}가 이미 보장하므로 엔티티가 따로 검증할 불변식이 없다.
 */
@Getter
@Entity
@Table(name = "user_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_preferences_user_id", columnNames = {"user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreference extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    /** 사용자가 입력한 MBTI. 아직 입력하지 않았을 수 있으므로 nullable. */
    @Enumerated(EnumType.STRING)
    @Column(name = "mbti", length = 4)
    private Mbti mbti;

    private UserPreference(UUID userId, Mbti mbti) {
        this.userId = userId;
        this.mbti = mbti;
    }

    /** 사용자의 선호 입력값을 최초로 생성한다. */
    public static UserPreference of(UUID userId, Mbti mbti) {
        return new UserPreference(userId, mbti);
    }

    /** MBTI를 갱신한다(재입력 시 최신 값으로 덮어쓴다 — 이력은 보관하지 않는다). */
    public void updateMbti(Mbti mbti) {
        this.mbti = mbti;
    }
}
