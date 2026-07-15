package com.soma.yeolo.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. 비밀값은 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValidityMs,
        long refreshTokenValidityMs
) {
}
