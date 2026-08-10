package com.soma.yeolo.share.domain;

import com.soma.yeolo.global.security.TokenHasher;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 공유 링크의 초대 식별자 (DOM-6 §보안 및 운영 정책 — "공유 링크에는 실제 courseId를 직접 노출하지
 * 않는다. 서버에서 관리하는 임시 초대 식별자를 기반으로 처리한다").
 *
 * <p>추측 불가능해야 하므로 {@link SecureRandom} 32바이트를 URL-safe Base64(패딩 없음, 43자)로
 * 인코딩한다. 경로 파라미터로 그대로 실려야 하므로 {@code +}·{@code /}가 없는 URL 알파벳을 쓴다.
 *
 * <p>DB에는 평문이 아니라 {@link #hash()}(SHA-256)만 저장한다 — Refresh Token과 같은 정책이다
 * (docs/architecture.md §3). 그 대가로 <b>발급된 토큰은 재조회할 수 없다</b>: 공유 링크 생성
 * (API-SHARE-1)은 호출할 때마다 새 토큰을 발급하며, 앞서 발급된 링크도 만료 전까지 함께 유효하다.
 */
public record ShareToken(String value) {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    /** 새 초대 식별자를 발급한다. */
    public static ShareToken issue() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return new ShareToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** 조회한 평문 토큰을 감싼다(수신 측). */
    public static ShareToken of(String value) {
        return new ShareToken(value);
    }

    /** DB 조회·저장에 쓰는 SHA-256 해시. */
    public String hash() {
        return TokenHasher.sha256Hex(value);
    }
}
