package com.soma.yeolo.auth.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Refresh Token 저장소. 평문이 아닌 해시를 저장한다. 사용자당 1개(회전 시 갱신).
 * (docs/architecture.md 3)
 */
@Getter
@Entity
@Table(name = "refresh_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "uk_refresh_tokens_user_id", columnNames = "user_id")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    private RefreshToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public static RefreshToken create(UUID userId, String tokenHash, Instant expiresAt) {
        return new RefreshToken(userId, tokenHash, expiresAt);
    }

    /** 회전(rotation) 시 해시/만료 갱신. */
    public void rotate(String tokenHash, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }
}
