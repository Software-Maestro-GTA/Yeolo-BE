package com.soma.yeolo.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google OAuth 설정. client-id/secret은 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 */
@ConfigurationProperties(prefix = "google.oauth")
public record GoogleOAuthProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String tokenUri,
        String userinfoUri
) {
}
