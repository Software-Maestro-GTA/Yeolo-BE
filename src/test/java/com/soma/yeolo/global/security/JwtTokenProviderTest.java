package com.soma.yeolo.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.security.JwtTokenProvider.GeneratedToken;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-key-for-yeolo-hs256-abcdefghijklmnop";

    private JwtTokenProvider provider(long accessTtlMs, long refreshTtlMs) {
        return new JwtTokenProvider(new JwtProperties(SECRET, accessTtlMs, refreshTtlMs));
    }

    @Test
    void 액세스_토큰의_subject로_userId를_복원한다() {
        JwtTokenProvider provider = provider(3600_000, 1209600_000);
        UUID userId = UUID.randomUUID();

        String token = provider.createAccessToken(userId);

        assertThat(provider.parseAccessTokenUserId(token)).isEqualTo(userId);
    }

    @Test
    void 리프레시_토큰은_만료시각을_함께_반환한다() {
        JwtTokenProvider provider = provider(3600_000, 1209600_000);
        UUID userId = UUID.randomUUID();

        GeneratedToken refresh = provider.createRefreshToken(userId);

        assertThat(refresh.token()).isNotBlank();
        assertThat(refresh.expiresAt()).isAfter(Instant.now());
        assertThat(provider.parseRefreshTokenUserId(refresh.token())).isEqualTo(userId);
    }

    @Test
    void 리프레시_토큰으로는_액세스_토큰_검증을_통과할_수_없다() {
        JwtTokenProvider provider = provider(3600_000, 1209600_000);
        String refreshToken = provider.createRefreshToken(UUID.randomUUID()).token();

        // 서명은 유효하지만 용도가 다르다 — 수명이 긴 Refresh로 보호 리소스에 접근하면 안 된다.
        assertThatThrownBy(() -> provider.parseAccessTokenUserId(refreshToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 액세스_토큰으로는_재발급용_검증을_통과할_수_없다() {
        JwtTokenProvider provider = provider(3600_000, 1209600_000);
        String accessToken = provider.createAccessToken(UUID.randomUUID());

        // Access로 재발급이 되면 세션을 무한 연장할 수 있다 (API-AUTH-3).
        assertThatThrownBy(() -> provider.parseRefreshTokenUserId(accessToken))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 위변조된_토큰은_파싱에_실패한다() {
        JwtTokenProvider provider = provider(3600_000, 1209600_000);

        assertThatThrownBy(() -> provider.parseAccessTokenUserId("not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void 만료된_토큰은_파싱에_실패한다() {
        JwtTokenProvider provider = provider(-1000, -1000); // 이미 만료
        String expired = provider.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> provider.parseAccessTokenUserId(expired))
                .isInstanceOf(Exception.class);
    }
}
