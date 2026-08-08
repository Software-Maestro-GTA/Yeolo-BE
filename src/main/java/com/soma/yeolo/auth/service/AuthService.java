package com.soma.yeolo.auth.service;

import com.soma.yeolo.auth.client.AppleOAuthClient;
import com.soma.yeolo.auth.client.GoogleOAuthClient;
import com.soma.yeolo.auth.client.dto.AppleUserInfo;
import com.soma.yeolo.auth.client.dto.GoogleUserInfo;
import com.soma.yeolo.auth.dto.AppleLoginRequest;
import com.soma.yeolo.auth.dto.AppleLoginResponse;
import com.soma.yeolo.auth.dto.GoogleLoginRequest;
import com.soma.yeolo.auth.dto.GoogleLoginResponse;
import com.soma.yeolo.auth.dto.TokenRefreshResponse;
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
import org.springframework.util.StringUtils;

/**
 * OAuth 로그인 오케스트레이션:
 * 인가 코드/토큰 → 제공자 인증 → 사용자 upsert → JWT 발급 → Refresh Token 저장.
 * (Google: API-AUTH-1, Apple: API-AUTH-2)
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final AppleOAuthClient appleOAuthClient;
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

    public AppleLoginResponse loginWithApple(AppleLoginRequest request) {
        // 1. Apple 인증 (외부 호출 — 트랜잭션 밖). id_token 검증 후 사용자 식별 정보를 추출한다.
        //    email_verified가 명시적으로 false면 인증 실패(401)로 본다(미제공/true는 통과).
        AppleUserInfo apple = appleOAuthClient.authenticate(
                request.code(), request.redirectUri(), request.idToken());
        if (apple.emailVerified() != null && !apple.emailVerified()) {
            throw new BusinessException(ErrorCode.APPLE_AUTH_FAILED);
        }

        // 2. 사용자 생성/조회. Apple은 id_token에 이름/프로필 이미지를 제공하지 않아 null로 둔다.
        User user = userService.upsertOnOAuthLogin(new OAuthUserInfo(
                Provider.APPLE, apple.sub(), apple.email(), null, null));

        // 3. JWT 발급 + Refresh Token 저장
        String accessToken = jwtTokenProvider.createAccessToken(user.getId());
        GeneratedToken refresh = jwtTokenProvider.createRefreshToken(user.getId());
        refreshTokenService.issue(user.getId(), refresh.token(), refresh.expiresAt());

        // 4. 응답 구성 (온보딩 유도 여부 포함)
        return AppleLoginResponse.from(user, resolveDoOnboarding(user.getId()), accessToken, refresh.token());
    }

    /**
     * 온보딩(Intro) 유도 여부. 취향 프로필과 코스를 모두 보유한 사용자는 온보딩을 마친 것으로 보고 false,
     * 둘 중 하나라도 없으면 true. 두 신호는 재로그인해도 유지되므로, 가입 직후 앱을 껐다 켜도
     * 온보딩을 끝내기 전까지는 계속 온보딩으로 유도된다.
     */
    private boolean resolveDoOnboarding(UUID userId) {
        return !(tasteProfileRepository.existsByUserId(userId) && courseRepository.existsByUserId(userId));
    }

    /**
     * 토큰 재발급 (API-AUTH-3): Refresh Token을 검증하고 Access/Refresh를 함께 재발급한다.
     *
     * <p>새 Refresh Token으로 회전시켜 저장하므로 방금 쓴 토큰은 즉시 무효가 된다(재사용 차단).
     * 검증 실패 사유(서명 위조·만료·용도 불일치·이미 무효화됨)는 모두 같은 401로 응답한다 —
     * 어느 쪽으로 틀렸는지 알려 주면 유효한 토큰을 찾는 데 단서가 되기 때문이다.
     *
     * <p>탈퇴·로그아웃한 사용자는 저장된 Refresh Token이 지워져 {@code matches}에서 걸린다.
     * 즉 저장 레코드의 존재 자체가 "살아 있는 세션"의 근거이므로 별도 사용자 상태 조회는 하지 않는다.
     */
    public TokenRefreshResponse refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UUID userId;
        try {
            userId = jwtTokenProvider.parseRefreshTokenUserId(refreshToken);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (!refreshTokenService.matches(userId, refreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String accessToken = jwtTokenProvider.createAccessToken(userId);
        GeneratedToken refresh = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.issue(userId, refresh.token(), refresh.expiresAt());
        return new TokenRefreshResponse(accessToken, refresh.token());
    }

    /** 로그아웃 (API-AUTH-4): 사용자의 Refresh Token을 무효화해 세션을 종료한다. */
    public void logout(UUID userId) {
        refreshTokenService.revoke(userId);
    }
}
