package com.soma.yeolo.auth.service;

import com.soma.yeolo.auth.client.GoogleOAuthClient;
import com.soma.yeolo.auth.client.dto.GoogleUserInfo;
import com.soma.yeolo.auth.dto.GoogleLoginRequest;
import com.soma.yeolo.auth.dto.GoogleLoginResponse;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.JwtTokenProvider.GeneratedToken;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.service.OAuthUserInfo;
import com.soma.yeolo.user.service.UserService;
import java.util.UUID;
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
    private final TasteProfileRepository tasteProfileRepository;
    private final CourseRepository courseRepository;

    public GoogleLoginResponse loginWithGoogle(GoogleLoginRequest request) {
        // 1. Google 인증 (외부 호출 — 트랜잭션 밖). 이메일 미검증은 인증 실패(401)로 본다.
        GoogleUserInfo google = googleOAuthClient.authenticate(request.code(), request.redirectUri());
        if (google.emailVerified() == null || !google.emailVerified()) {
            throw new BusinessException(ErrorCode.GOOGLE_AUTH_FAILED);
        }

        // 2. 사용자 생성/조회
        User user = userService.upsertOnOAuthLogin(new OAuthUserInfo(
                Provider.GOOGLE, google.sub(), google.email(), google.name(), google.picture()));

        // 3. JWT 발급 + Refresh Token 저장
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        GeneratedToken refresh = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.issue(user.getId(), refresh.token(), refresh.expiresAt());

        // 4. 응답 구성 (온보딩 유도 여부 포함)
        return GoogleLoginResponse.from(user, resolveDoOnboarding(user.getId()), accessToken, refresh.token());
    }

    /**
     * 온보딩(Intro) 유도 여부. 취향 프로필과 코스를 모두 보유한 사용자는 온보딩을 마친 것으로 보고 false,
     * 둘 중 하나라도 없으면 true. 두 신호는 재로그인해도 유지되므로, 가입 직후 앱을 껐다 켜도
     * 온보딩을 끝내기 전까지는 계속 온보딩으로 유도된다.
     */
    private boolean resolveDoOnboarding(UUID userId) {
        return !(tasteProfileRepository.existsByUserId(userId) && courseRepository.existsByUserId(userId));
    }

    /** 로그아웃 (API-FB-11): 사용자의 Refresh Token을 무효화해 세션을 종료한다. */
    public void logout(UUID userId) {
        refreshTokenService.revoke(userId);
    }
}
