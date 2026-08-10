package com.soma.yeolo.share.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 공유 링크 설정의 만료·URL 조립 규칙 (API-SHARE-1). */
class ShareLinkPropertiesTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void ttl만큼_뒤가_만료_시각이다() {
        ShareLinkProperties properties =
                new ShareLinkProperties("https://yeolo.app/share", Duration.ofDays(7));

        assertThat(properties.expiresAtFrom(ISSUED_AT)).isEqualTo(Instant.parse("2026-08-17T12:00:00Z"));
    }

    /** 명세의 {@code expiresAt|null}(무기한)을 설정으로 표현하는 방법이다. */
    @Test
    void ttl이_0이하거나_없으면_무기한이다() {
        assertThat(new ShareLinkProperties("https://x", Duration.ZERO).expiresAtFrom(ISSUED_AT)).isNull();
        assertThat(new ShareLinkProperties("https://x", Duration.ofDays(-1)).expiresAtFrom(ISSUED_AT))
                .isNull();
        assertThat(new ShareLinkProperties("https://x", null).expiresAtFrom(ISSUED_AT)).isNull();
    }

    @Test
    void 기준_URL_뒤에_토큰을_붙인다() {
        ShareLinkProperties properties =
                new ShareLinkProperties("https://yeolo.app/share", Duration.ofDays(7));

        assertThat(properties.shareUrl("abc123")).isEqualTo("https://yeolo.app/share/abc123");
    }

    /** 환경변수로 주입되는 값이라 끝 슬래시가 붙어 오기 쉽다 — 슬래시가 겹치지 않아야 한다. */
    @Test
    void 기준_URL의_끝_슬래시는_무시한다() {
        ShareLinkProperties properties =
                new ShareLinkProperties("https://yeolo.app/share//", Duration.ofDays(7));

        assertThat(properties.shareUrl("abc123")).isEqualTo("https://yeolo.app/share/abc123");
    }
}
