package com.soma.yeolo.auth.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Apple Sign In 설정. team-id/key-id/private-key(.p8)는 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 *
 * <ul>
 *   <li>{@code clientId}  — Services ID(웹) 또는 App ID(네이티브). id_token의 {@code aud} 검증 및 client_secret의 {@code sub}.</li>
 *   <li>{@code teamId}    — Apple Developer Team ID. client_secret의 {@code iss}.</li>
 *   <li>{@code keyId}     — .p8 개인키의 Key ID. client_secret JWT 헤더의 {@code kid}.</li>
 *   <li>{@code privateKey}— .p8 개인키(PKCS#8, PEM 본문). ES256 client_secret 서명에 사용.</li>
 *   <li>{@code tokenUri}  — 인가 코드 → 토큰 교환 엔드포인트.</li>
 *   <li>{@code jwksUri}   — id_token 서명 검증용 Apple 공개키(JWKS) 엔드포인트.</li>
 *   <li>{@code issuer}    — id_token의 {@code iss} 기대값(https://appleid.apple.com).</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "apple.oauth")
public record AppleOAuthProperties(
        String clientId,
        String teamId,
        String keyId,
        String privateKey,
        String tokenUri,
        String jwksUri,
        String issuer
) {
}
