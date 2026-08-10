package com.soma.yeolo.share.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * 신규 저장용 공유 링크 (DOM-6). 코스 소유자가 발급한 초대 식별자를 코스·초대자에 연결한다.
 * 부여된 식별자·상태를 함께 담는 읽기 모델은 {@link SavedShareLink}이다.
 *
 * @param courseId  공유 대상 코스
 * @param inviterId 링크를 발급한 사용자(코스 소유자)
 * @param tokenHash 초대 식별자의 SHA-256 해시 (평문 저장 금지 — {@link ShareToken})
 * @param expiresAt 만료 시각. {@code null}이면 무기한 (API-SHARE-1 응답의 {@code expiresAt|null})
 */
public record ShareLink(
        UUID courseId,
        UUID inviterId,
        String tokenHash,
        Instant expiresAt
) {
}
