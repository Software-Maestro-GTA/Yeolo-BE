package com.soma.yeolo.user.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 정보 (DOM-3). Google OAuth 기반 사용자 계정.
 * (provider, provider_user_id) 조합으로 유일 식별한다.
 */
@Getter
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_provider_provider_user_id",
                columnNames = {"provider", "provider_user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider", nullable = false)
    private Provider provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(name = "email")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(name = "status", nullable = false)
    private UserStatus status;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Builder
    private User(Provider provider, String providerUserId, String email,
                 String displayName, String profileImageUrl) {
        this.provider = provider;
        this.providerUserId = providerUserId;
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.status = UserStatus.ACTIVE;
        this.lastLoginAt = Instant.now();
    }

    /** 신규 OAuth 사용자 생성. status=active, 최초 로그인 시각 기록. */
    public static User createOAuthUser(Provider provider, String providerUserId, String email,
                                       String displayName, String profileImageUrl) {
        return User.builder()
                .provider(provider)
                .providerUserId(providerUserId)
                .email(email)
                .displayName(displayName)
                .profileImageUrl(profileImageUrl)
                .build();
    }

    /** 기존 사용자 재로그인 시 프로필 최신화 + 마지막 로그인 시각 갱신. */
    public void updateOnLogin(String email, String displayName, String profileImageUrl) {
        this.email = email;
        this.displayName = displayName;
        this.profileImageUrl = profileImageUrl;
        this.lastLoginAt = Instant.now();
    }

    /**
     * 사용자가 직접 수정한 프로필을 반영한다 (API-USER-1).
     *
     * <p>{@code null}인 항목은 <b>변경하지 않는다</b>. PATCH이고 DOM-1상 세 항목 모두 nullable이라
     * "안 보냄"과 "null로 지움"을 요청 본문만으로 구분할 수 없는데, 안 보낸 항목을 null로 덮으면
     * 이름만 고쳐도 이메일이 지워진다. 지우는 쪽이 아니라 유지하는 쪽을 기본값으로 둔다.
     */
    public void updateProfile(String email, String displayName, String profileImageUrl) {
        if (email != null) {
            this.email = email;
        }
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }
}
