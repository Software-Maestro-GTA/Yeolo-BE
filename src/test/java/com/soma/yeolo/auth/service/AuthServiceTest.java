package com.soma.yeolo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private GoogleOAuthClient googleOAuthClient;
    @Mock
    private AppleOAuthClient appleOAuthClient;
    @Mock
    private UserService userService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private TasteProfileRepository tasteProfileRepository;
    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private AuthService authService;

    /** 구글 인증 성공 흐름을 스텁한다. onboarding 신호(취향/코스 보유 여부)는 인자로 제어한다. */
    private User stubGoogleLoginSuccess(UUID userId, boolean hasTasteProfile, boolean hasCourse) {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1", "u@gmail.com", "홍길동", "http://img");
        ReflectionTestUtils.setField(user, "id", userId);

        when(googleOAuthClient.authenticate("auth-code", "http://localhost/callback"))
                .thenReturn(new GoogleUserInfo("sub-1", "u@gmail.com", "홍길동", "http://img", true));
        when(userService.upsertOnOAuthLogin(any(OAuthUserInfo.class))).thenReturn(user);
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(userId))
                .thenReturn(new GeneratedToken("refresh-token", Instant.now().plusSeconds(1000)));
        when(tasteProfileRepository.existsByUserId(userId)).thenReturn(hasTasteProfile);
        // 코스 조회는 취향 프로필이 있을 때만 도달한다(doOnboarding의 && 단락 평가).
        if (hasTasteProfile) {
            when(courseRepository.existsByUserId(userId)).thenReturn(hasCourse);
        }
        return user;
    }

    private GoogleLoginResponse login() {
        return authService.loginWithGoogle(new GoogleLoginRequest("auth-code", "http://localhost/callback"));
    }

    @Test
    void 구글_로그인은_사용자를_upsert하고_토큰을_발급한다() {
        UUID userId = UUID.randomUUID();
        stubGoogleLoginSuccess(userId, false, false);

        GoogleLoginResponse response = login();

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().userId()).isEqualTo(userId.toString());
        assertThat(response.user().provider()).isEqualTo("google");
        assertThat(response.user().email()).isEqualTo("u@gmail.com");
        assertThat(response.user().displayName()).isEqualTo("홍길동");
        assertThat(response.user().profileImageUrl()).isEqualTo("http://img");
        assertThat(response.user().status()).isEqualTo("active");
        assertThat(response.user().lastLoginAt()).isNotNull();

        verify(refreshTokenService).issue(eq(userId), eq("refresh-token"), any(Instant.class));
    }

    @Test
    void 취향프로필과_코스가_모두_없으면_doOnboarding은_true다() {
        UUID userId = UUID.randomUUID();
        stubGoogleLoginSuccess(userId, false, false);

        assertThat(login().doOnboarding()).isTrue();
    }

    @Test
    void 취향프로필만_있고_코스가_없으면_doOnboarding은_true다() {
        UUID userId = UUID.randomUUID();
        stubGoogleLoginSuccess(userId, true, false);

        assertThat(login().doOnboarding()).isTrue();
    }

    @Test
    void 취향프로필과_코스를_모두_보유하면_doOnboarding은_false다() {
        UUID userId = UUID.randomUUID();
        stubGoogleLoginSuccess(userId, true, true);

        assertThat(login().doOnboarding()).isFalse();
    }

    @Test
    void 이메일_미검증이면_인증실패_401로_처리하고_사용자를_생성하지_않는다() {
        when(googleOAuthClient.authenticate("auth-code", "http://localhost/callback"))
                .thenReturn(new GoogleUserInfo("sub-1", "u@gmail.com", "홍길동", "http://img", false));

        assertThatThrownBy(this::login)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.GOOGLE_AUTH_FAILED);

        verifyNoInteractions(userService, jwtTokenProvider, refreshTokenService,
                tasteProfileRepository, courseRepository);
    }

    /** 애플 인증 성공 흐름을 스텁한다. emailVerified 신호는 인자로 제어한다. */
    private User stubAppleLoginSuccess(UUID userId, String email, Boolean emailVerified) {
        User user = User.createOAuthUser(Provider.APPLE, "apple-sub", email, null, null);
        ReflectionTestUtils.setField(user, "id", userId);

        when(appleOAuthClient.authenticate("apple-code", "http://localhost/callback", "id-token"))
                .thenReturn(new AppleUserInfo("apple-sub", email, emailVerified));
        when(userService.upsertOnOAuthLogin(any(OAuthUserInfo.class))).thenReturn(user);
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(userId))
                .thenReturn(new GeneratedToken("refresh-token", Instant.now().plusSeconds(1000)));
        when(tasteProfileRepository.existsByUserId(userId)).thenReturn(false);
        return user;
    }

    private AppleLoginResponse appleLogin() {
        return authService.loginWithApple(
                new AppleLoginRequest("apple-code", "http://localhost/callback", "id-token"));
    }

    @Test
    void 애플_로그인은_사용자를_upsert하고_토큰을_발급한다() {
        UUID userId = UUID.randomUUID();
        stubAppleLoginSuccess(userId, "u@privaterelay.appleid.com", true);

        AppleLoginResponse response = appleLogin();

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().userId()).isEqualTo(userId.toString());
        assertThat(response.user().provider()).isEqualTo("apple");
        assertThat(response.user().email()).isEqualTo("u@privaterelay.appleid.com");
        // Apple은 id_token에 이름/프로필 이미지를 제공하지 않는다.
        assertThat(response.user().displayName()).isNull();
        assertThat(response.user().profileImageUrl()).isNull();
        assertThat(response.user().status()).isEqualTo("active");
        assertThat(response.doOnboarding()).isTrue();

        verify(refreshTokenService).issue(eq(userId), eq("refresh-token"), any(Instant.class));
    }

    @Test
    void 애플_email_verified가_미제공이면_인증을_통과시킨다() {
        // Apple 재로그인 시 email/email_verified가 생략될 수 있다. sub 검증만 통과하면 로그인 성공.
        UUID userId = UUID.randomUUID();
        stubAppleLoginSuccess(userId, null, null);

        assertThat(appleLogin().user().provider()).isEqualTo("apple");
    }

    @Test
    void 애플_email_verified가_false면_인증실패_401로_처리하고_사용자를_생성하지_않는다() {
        when(appleOAuthClient.authenticate("apple-code", "http://localhost/callback", "id-token"))
                .thenReturn(new AppleUserInfo("apple-sub", "u@privaterelay.appleid.com", false));

        assertThatThrownBy(this::appleLogin)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.APPLE_AUTH_FAILED);

        verifyNoInteractions(userService, jwtTokenProvider, refreshTokenService,
                tasteProfileRepository, courseRepository);
    }

    @Test
    void 로그아웃은_사용자의_Refresh_Token을_무효화한다() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(refreshTokenService).revoke(eq(userId));
    }

    @Test
    void 재발급은_토큰을_검증하고_Access와_Refresh를_함께_회전시킨다() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(1209600);
        when(jwtTokenProvider.parseRefreshTokenUserId("old-refresh")).thenReturn(userId);
        when(refreshTokenService.matches(userId, "old-refresh")).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("new-access");
        when(jwtTokenProvider.createRefreshToken(userId))
                .thenReturn(new GeneratedToken("new-refresh", expiresAt));

        TokenRefreshResponse response = authService.refresh("old-refresh");

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
        // 새 토큰을 저장해야 방금 쓴 토큰이 무효가 된다(재사용 차단).
        verify(refreshTokenService).issue(userId, "new-refresh", expiresAt);
    }

    @Test
    void 재발급_토큰이_없으면_401로_처리하고_토큰을_발급하지_않는다() {
        assertThatThrownBy(() -> authService.refresh(null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    void 서명이_깨진_재발급_토큰은_401로_처리한다() {
        when(jwtTokenProvider.parseRefreshTokenUserId("broken"))
                .thenThrow(new IllegalArgumentException("bad token"));

        assertThatThrownBy(() -> authService.refresh("broken"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void 무효화됐거나_이미_회전된_재발급_토큰은_401로_처리한다() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseRefreshTokenUserId("stale")).thenReturn(userId);
        // 서명·만료는 멀쩡해도 저장된 토큰과 다르면(로그아웃·탈퇴·회전) 살아 있는 세션이 아니다.
        when(refreshTokenService.matches(userId, "stale")).thenReturn(false);

        assertThatThrownBy(() -> authService.refresh("stale"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);

        verify(jwtTokenProvider, never()).createAccessToken(userId);
    }
}
