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

        assertThat(provider.parseUserId(token)).isEqualTo(userId);
    }

    @Test
    void 리프레시_토큰은_만료시각을_함께_반환한다() {
        JwtTokenProvider provider = provider(3600_000, 1209600_000);
        UUID userId = UUID.randomUUID();

        GeneratedToken refresh = provider.createRefreshToken(userId);

        assertThat(refresh.token()).isNotBlank();
        assertThat(refresh.expiresAt()).isAfter(Instant.now());
        assertThat(provider.parseUserId(refresh.token())).isEqualTo(userId);
    }

    @Test
    void 위변조된_토큰은_파싱에_실패한다() {
        JwtTokenProvider provider = provider(3600_000, 1209600_000);

        assertThatThrownBy(() -> provider.parseUserId("not.a.valid.token"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void 만료된_토큰은_파싱에_실패한다() {
        JwtTokenProvider provider = provider(-1000, -1000); // 이미 만료
        String expired = provider.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> provider.parseUserId(expired))
                .isInstanceOf(Exception.class);
    }
}
