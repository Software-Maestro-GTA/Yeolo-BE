package com.soma.yeolo.share.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.share.domain.SavedShareLink;
import com.soma.yeolo.share.domain.ShareLink;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 여행 코스 친구 초대 공유 링크 (DOM-6). 테이블 스펙이 명세에 없어 BE에서 정한 스키마다
 * (docs/ddl/course_share_links.sql).
 *
 * <p>초대 식별자는 평문이 아니라 SHA-256 해시로 저장하며, 조회는 해시로만 한다. 유니크 제약을
 * 걸어 두는 이유는 조회 인덱스이자 충돌 방어선이기 때문이다.
 */
@Getter
@Entity
@Table(name = "course_share_links",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_course_share_links_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_course_share_links_course_id", columnList = "course_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShareLinkEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    @Column(name = "inviter_id", nullable = false)
    private UUID inviterId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    /** {@code null}이면 무기한 (API-SHARE-1 응답의 {@code expiresAt|null}). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** {@code null}이면 유효. 원본 코스 삭제 시 채워진다. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    private ShareLinkEntity(UUID courseId, UUID inviterId, String tokenHash, Instant expiresAt) {
        this.courseId = courseId;
        this.inviterId = inviterId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    /** 순수 도메인 → 영속 엔티티 매핑. */
    public static ShareLinkEntity from(ShareLink link) {
        return new ShareLinkEntity(link.courseId(), link.inviterId(), link.tokenHash(), link.expiresAt());
    }

    /** 영속 엔티티 → 조회용 읽기 모델 매핑. */
    public SavedShareLink toSavedShareLink() {
        return new SavedShareLink(id, courseId, inviterId, expiresAt, revokedAt);
    }

    /** 링크를 회수한다. 이미 회수됐으면 시각을 덮어쓰지 않는다(최초 회수 시점 보존). */
    public void revoke(Instant revokedAt) {
        if (this.revokedAt == null) {
            this.revokedAt = revokedAt;
        }
    }
}
