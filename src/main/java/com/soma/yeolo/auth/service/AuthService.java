package com.soma.yeolo.auth.service;

import com.soma.yeolo.auth.client.GoogleOAuthClient;
import com.soma.yeolo.auth.client.dto.GoogleUserInfo;
import com.soma.yeolo.auth.dto.GoogleLoginRequest;
import com.soma.yeolo.auth.dto.GoogleLoginResponse;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.JwtTokenProvider.GeneratedToken;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.service.OAuthUserInfo;
import com.soma.yeolo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Google OAuth 로그인 오케스트레이션 (API-FB-1):
 * 인가 코드 → Google 인증 → 사용자 upsert → JWT 발급 → Refresh Token 저장.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public GoogleLoginResponse loginWithGoogle(GoogleLoginRequest request) {
        // 1. Google 인증 (외부 호출 — 트랜잭션 밖)
        GoogleUserInfo google = googleOAuthClient.authenticate(request.code(), request.redirectUri());
        if (google.emailVerified() == null || !google.emailVerified()) {
            throw new com.soma.yeolo.global.exception.BusinessException(
                    com.soma.yeolo.global.exception.ErrorCode.INVALID_GOOGLE_CODE);
        }

        // 2. 사용자 생성/조회
        User user = userService.upsertOnOAuthLogin(new OAuthUserInfo(
                Provider.GOOGLE, google.sub(), google.email(), google.name(), google.picture()));

        // 3. JWT 발급 + Refresh Token 저장
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        GeneratedToken refresh = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.issue(user.getId(), refresh.token(), refresh.expiresAt());

        // 4. 응답 구성
        return GoogleLoginResponse.from(user, accessToken, refresh.token());
    }
}
