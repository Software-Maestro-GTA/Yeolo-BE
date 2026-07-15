package com.soma.yeolo.auth.service;

import com.soma.yeolo.auth.entity.RefreshToken;
import com.soma.yeolo.auth.repository.RefreshTokenRepository;
import com.soma.yeolo.global.security.TokenHasher;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    /** 사용자당 1개의 Refresh Token을 해시로 저장한다. 이미 있으면 회전(갱신). */
    @Transactional
    public void issue(UUID userId, String rawToken, Instant expiresAt) {
        String hash = TokenHasher.sha256Hex(rawToken);
        refreshTokenRepository.findByUserId(userId)
                .ifPresentOrElse(
                        token -> token.rotate(hash, expiresAt),
                        () -> refreshTokenRepository.save(RefreshToken.create(userId, hash, expiresAt))
                );
    }
}
