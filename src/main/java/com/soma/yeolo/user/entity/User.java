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
}
