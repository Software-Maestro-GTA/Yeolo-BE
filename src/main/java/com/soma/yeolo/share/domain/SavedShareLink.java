package com.soma.yeolo.share.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 저장된 공유 링크 조회 결과 (API-SHARE-2 / API-SHARE-3). 발급용 {@link ShareLink}와 달리 부여된
 * 식별자와 회수 상태를 함께 담는 읽기 전용 모델이다.
 *
 * <p>"쓸 수 있는 링크"의 판정({@link #isUsable(Instant)})을 이 모델이 소유한다 — 만료·회수는
 * 조회(410)와 수락(410) 양쪽에서 같은 규칙으로 걸러져야 하기 때문이다.
 *
 * @param id        공유 링크 식별자
 * @param courseId  공유 대상 코스
 * @param inviterId 링크를 발급한 사용자(코스 소유자)
 * @param expiresAt 만료 시각. {@code null}이면 무기한
 * @param revokedAt 회수 시각. {@code null}이면 회수되지 않음
 */
public record SavedShareLink(
        UUID id,
        UUID courseId,
        UUID inviterId,
        Instant expiresAt,
        Instant revokedAt
) {

    /** 회수된 링크인지. 원본 코스가 삭제되면 그 코스의 링크는 함께 회수된다. */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** 만료된 링크인지. 만료 시각이 없으면(무기한) 언제나 만료되지 않는다. */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    /** 조회·수락에 쓸 수 있는 링크인지. 아니면 명세의 410으로 응답한다. */
    public boolean isUsable(Instant now) {
        return !isRevoked() && !isExpired(now);
    }
}
