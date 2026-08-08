package com.soma.yeolo.global.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Access/Refresh JWT 발급·검증. HS256 서명. (docs/architecture.md 3)
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = properties.accessTokenValidityMs();
        this.refreshTokenValidityMs = properties.refreshTokenValidityMs();
    }

    public String createAccessToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(accessTokenValidityMs)))
                .signWith(key)
                .compact();
    }

    public GeneratedToken createRefreshToken(UUID userId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(refreshTokenValidityMs);
        String token = Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new GeneratedToken(token, expiresAt);
    }

    /**
     * Access Token을 검증하고 사용자 식별자(subject)를 반환한다. 유효하지 않으면 예외.
     * 보호 리소스 접근에 쓰인다.
     */
    public UUID parseAccessTokenUserId(String token) {
        return parseUserId(token, TYPE_ACCESS);
    }

    /**
     * Refresh Token을 검증하고 사용자 식별자(subject)를 반환한다. 유효하지 않으면 예외.
     * 토큰 재발급(API-AUTH-3)에 쓰인다.
     */
    public UUID parseRefreshTokenUserId(String token) {
        return parseUserId(token, TYPE_REFRESH);
    }

    /**
     * 서명·만료를 검증한 뒤 {@code type} 클레임이 기대값과 같은지까지 확인한다.
     *
     * <p>두 토큰은 같은 키로 서명되므로 type을 보지 않으면 서로를 대신할 수 있다 — 수명이 긴
     * Refresh Token으로 보호 리소스에 접근하거나(Access Token 수명 제한이 무의미해진다),
     * Access Token으로 재발급을 받아 세션을 무한 연장하는 경로가 열린다. 용도별로 못을 박는다.
     */
    private UUID parseUserId(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String type = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(type)) {
            throw new IllegalArgumentException(
                    "Unexpected token type: expected=%s, actual=%s".formatted(expectedType, type));
        }
        return UUID.fromString(claims.getSubject());
    }

    public record GeneratedToken(String token, Instant expiresAt) {
    }
}
