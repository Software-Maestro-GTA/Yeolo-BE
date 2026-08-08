package com.soma.yeolo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.soma.yeolo.auth.entity.RefreshToken;
import com.soma.yeolo.auth.repository.RefreshTokenRepository;
import com.soma.yeolo.global.security.TokenHasher;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private static RefreshToken stored(UUID userId, String rawToken, Instant expiresAt) {
        return RefreshToken.create(userId, TokenHasher.sha256Hex(rawToken), expiresAt);
    }

    @Test
    void 저장된_토큰과_같고_만료_전이면_일치로_본다() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.findByUserId(userId))
                .thenReturn(Optional.of(stored(userId, "raw-token", Instant.now().plusSeconds(600))));

        assertThat(refreshTokenService.matches(userId, "raw-token")).isTrue();
    }

    @Test
    void 회전으로_교체된_옛_토큰은_일치하지_않는다() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.findByUserId(userId))
                .thenReturn(Optional.of(stored(userId, "new-token", Instant.now().plusSeconds(600))));

        // 서명은 유효해도 저장된 해시가 이미 새 토큰으로 바뀌었다.
        assertThat(refreshTokenService.matches(userId, "old-token")).isFalse();
    }

    @Test
    void 저장_레코드가_만료됐으면_일치하지_않는다() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.findByUserId(userId))
                .thenReturn(Optional.of(stored(userId, "raw-token", Instant.now().minusSeconds(1))));

        assertThat(refreshTokenService.matches(userId, "raw-token")).isFalse();
    }

    @Test
    void 로그아웃_탈퇴로_삭제된_뒤에는_일치하지_않는다() {
        UUID userId = UUID.randomUUID();
        when(refreshTokenRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThat(refreshTokenService.matches(userId, "raw-token")).isFalse();
    }
}
