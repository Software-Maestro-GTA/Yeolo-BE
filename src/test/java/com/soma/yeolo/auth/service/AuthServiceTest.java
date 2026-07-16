package com.soma.yeolo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private UserService userService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    @Test
    void 구글_로그인은_사용자를_upsert하고_토큰을_발급한다() {
        UUID userId = UUID.randomUUID();
        Instant refreshExpiry = Instant.now().plusSeconds(1000);

        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1", "u@gmail.com", "홍길동", "http://img");
        ReflectionTestUtils.setField(user, "id", userId);

        when(googleOAuthClient.authenticate("auth-code", "http://localhost/callback"))
                .thenReturn(new GoogleUserInfo("sub-1", "u@gmail.com", "홍길동", "http://img", true));
        when(userService.upsertOnOAuthLogin(any(OAuthUserInfo.class))).thenReturn(user);
        when(jwtTokenProvider.createAccessToken(userId)).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(userId))
                .thenReturn(new GeneratedToken("refresh-token", refreshExpiry));

        GoogleLoginResponse response =
                authService.loginWithGoogle(new GoogleLoginRequest("auth-code", "http://localhost/callback"));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.user().userId()).isEqualTo(userId.toString());
        assertThat(response.user().provider()).isEqualTo("google");
        assertThat(response.user().email()).isEqualTo("u@gmail.com");
        assertThat(response.user().displayName()).isEqualTo("홍길동");
        assertThat(response.user().profileImageUrl()).isEqualTo("http://img");
        assertThat(response.user().status()).isEqualTo("active");
        assertThat(response.user().lastLoginAt()).isNotNull();

        verify(refreshTokenService).issue(eq(userId), eq("refresh-token"), eq(refreshExpiry));
    }
}
