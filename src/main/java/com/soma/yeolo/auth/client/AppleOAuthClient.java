package com.soma.yeolo.auth.client;

import com.soma.yeolo.auth.client.dto.AppleTokenResponse;
import com.soma.yeolo.auth.client.dto.AppleUserInfo;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwk;
import io.jsonwebtoken.security.JwkSet;
import io.jsonwebtoken.security.Jwks;
import java.security.Key;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Apple Sign In 호출 어댑터. (API-AUTH-2)
 *
 * <p>Google과 달리 별도 userinfo 엔드포인트가 없고, 사용자 식별 정보는 <b>id_token(JWT)</b>에 담겨 온다.
 * 클라이언트가 {@code idToken}을 함께 넘기면 그 토큰을 바로 검증하고, 없으면 {@code code}를 Apple 토큰
 * 엔드포인트에서 교환해 id_token을 얻는다. 코드 교환에 필요한 client_secret은 .p8 개인키로 서명한
 * ES256 JWT다. id_token은 Apple JWKS 공개키로 서명·발급자(iss)·대상(aud)을 검증한다.</p>
 *
 * <p>토큰이 거부되는 인증 실패(401/403)와 서명/클레임 검증 실패는 {@code APPLE_AUTH_FAILED}(401),
 * 그 외 4xx(잘못된 인가 코드)는 {@code INVALID_APPLE_CODE}(400), 통신/서버·설정 오류는
 * {@code APPLE_AUTH_SERVER_ERROR}(500)로 매핑한다.</p>
 */
@Slf4j
@Component
public class AppleOAuthClient {

    private final RestClient restClient;
    private final AppleOAuthProperties properties;

    public AppleOAuthClient(RestClient restClient, AppleOAuthProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * {@code idToken}이 있으면 그대로 검증하고, 없으면 {@code code}를 교환해 얻은 id_token을 검증한다.
     * 어느 경로든 검증된 클레임에서 {@link AppleUserInfo}를 추출해 반환한다.
     */
    public AppleUserInfo authenticate(String code, String redirectUri, String idToken) {
        String token = StringUtils.hasText(idToken) ? idToken : exchangeCode(code, redirectUri).idToken();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR);
        }
        return parseIdToken(token);
    }

    /** 인가 코드 → Apple 토큰 교환. client_secret은 .p8 개인키로 서명한 ES256 JWT다. */
    private AppleTokenResponse exchangeCode(String code, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", properties.clientId());
        form.add("client_secret", buildClientSecret());
        form.add("redirect_uri", StringUtils.hasText(redirectUri) ? redirectUri : "");
        form.add("grant_type", "authorization_code");

        try {
            AppleTokenResponse response = restClient.post()
                    .uri(properties.tokenUri())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AppleTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.idToken())) {
                throw new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR);
            }
            return response;
        } catch (RestClientResponseException e) {
            throw toBusinessException("token exchange", e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Apple token exchange failed (connectivity)", e);
            throw new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR, e);
        }
    }

    /** Apple JWKS 공개키로 id_token 서명·iss·aud를 검증하고 사용자 정보를 추출한다. */
    private AppleUserInfo parseIdToken(String idToken) {
        try {
            Claims claims = Jwts.parser()
                    .keyLocator(header -> resolveSigningKey((String) header.get("kid")))
                    .requireIssuer(properties.issuer())
                    .requireAudience(properties.clientId())
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();

            String sub = claims.getSubject();
            if (!StringUtils.hasText(sub)) {
                throw new BusinessException(ErrorCode.APPLE_AUTH_FAILED);
            }
            return new AppleUserInfo(sub, claims.get("email", String.class), readEmailVerified(claims));
        } catch (BusinessException e) {
            throw e;
        } catch (JwtException e) {
            // 서명 불일치·만료·iss/aud 불일치 등은 인증 실패(401)로 본다.
            log.warn("Apple id_token verification failed: {}", e.getMessage());
            throw new BusinessException(ErrorCode.APPLE_AUTH_FAILED, e);
        } catch (Exception e) {
            log.error("Apple id_token verification error", e);
            throw new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR, e);
        }
    }

    /** {@code kid}에 해당하는 Apple 공개키를 JWKS에서 조회한다. */
    private Key resolveSigningKey(String kid) {
        if (!StringUtils.hasText(kid)) {
            throw new BusinessException(ErrorCode.APPLE_AUTH_FAILED);
        }
        String jwksJson;
        try {
            jwksJson = restClient.get()
                    .uri(properties.jwksUri())
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("Apple JWKS fetch failed", e);
            throw new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR, e);
        }
        if (!StringUtils.hasText(jwksJson)) {
            throw new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR);
        }
        JwkSet jwkSet = Jwks.setParser().build().parse(jwksJson);
        for (Jwk<?> jwk : jwkSet.getKeys()) {
            if (kid.equals(jwk.getId())) {
                return jwk.toKey();
            }
        }
        // kid에 맞는 공개키가 없으면 서명을 신뢰할 수 없으므로 인증 실패로 본다.
        throw new BusinessException(ErrorCode.APPLE_AUTH_FAILED);
    }

    /** email_verified는 Apple에서 boolean 또는 문자열("true")로 올 수 있어 둘 다 처리한다. */
    private Boolean readEmailVerified(Claims claims) {
        Object value = claims.get("email_verified");
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /** iss=Team ID, sub=client_id, aud=Apple, kid=Key ID인 ES256 서명 JWT(client_secret)를 만든다. */
    private String buildClientSecret() {
        try {
            long now = System.currentTimeMillis();
            return Jwts.builder()
                    .header().keyId(properties.keyId()).and()
                    .issuer(properties.teamId())
                    .issuedAt(new Date(now))
                    .expiration(new Date(now + 300_000L)) // 5분 (Apple 상한 6개월, 짧게 발급)
                    .audience().add(properties.issuer()).and()
                    .subject(properties.clientId())
                    .signWith(loadPrivateKey(), Jwts.SIG.ES256)
                    .compact();
        } catch (Exception e) {
            log.error("Apple client_secret 생성 실패 (개인키 설정 확인)", e);
            throw new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR, e);
        }
    }

    /** .p8(PKCS#8, EC) 개인키 PEM 본문을 EC PrivateKey로 로드한다. */
    private PrivateKey loadPrivateKey() throws Exception {
        String pem = properties.privateKey()
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private BusinessException toBusinessException(String phase, RestClientResponseException e) {
        if (e.getStatusCode().is4xxClientError()) {
            // 401/403은 자격증명 거부 = 인증 실패(401), 그 외 4xx는 잘못된 인가 코드(400).
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                log.warn("Apple {} auth failed: {} {}", phase, e.getStatusCode(), e.getResponseBodyAsString());
                return new BusinessException(ErrorCode.APPLE_AUTH_FAILED, e);
            }
            log.warn("Apple {} rejected request: {} {}", phase, e.getStatusCode(), e.getResponseBodyAsString());
            return new BusinessException(ErrorCode.INVALID_APPLE_CODE, e);
        }
        log.error("Apple {} server error: {}", phase, e.getStatusCode(), e);
        return new BusinessException(ErrorCode.APPLE_AUTH_SERVER_ERROR, e);
    }
}
