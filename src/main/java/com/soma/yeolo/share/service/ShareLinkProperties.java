package com.soma.yeolo.share.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공유 링크 설정 (API-SHARE-1 / DOM-6). 만료 정책과 링크 형태는 명세가 정하지 않아 BE에서 정했다
 * (docs/sprint-scope.md).
 *
 * @param baseUrl 공유 링크의 기준 URL. 뒤에 {@code /{shareToken}}이 붙는다. 끝 슬래시는 무시한다
 * @param ttl     링크 수명. 0 이하면 무기한({@code expiresAt: null})으로 발급한다
 */
@ConfigurationProperties(prefix = "share-link")
public record ShareLinkProperties(String baseUrl, Duration ttl) {

    /** 발급 시각 기준 만료 시각. 무기한 설정이면 {@code null}. */
    public Instant expiresAtFrom(Instant issuedAt) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return null;
        }
        return issuedAt.plus(ttl);
    }

    /** 초대 식별자를 붙인 공유 URL. */
    public String shareUrl(String shareToken) {
        String base = baseUrl == null ? "" : baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + shareToken;
    }
}
