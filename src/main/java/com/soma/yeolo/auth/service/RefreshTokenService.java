package com.soma.yeolo.auth.service;

import com.soma.yeolo.auth.entity.RefreshToken;
import com.soma.yeolo.auth.repository.RefreshTokenRepository;
import com.soma.yeolo.global.security.TokenHasher;
import com.soma.yeolo.user.service.port.RefreshTokenRevoker;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenService implements RefreshTokenRevoker {

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

    /**
     * 제시된 Refresh Token이 현재 저장된 토큰과 일치하고 아직 유효한지 (API-AUTH-3).
     *
     * <p>JWT 서명·만료 검증만으로는 부족하다 — 로그아웃·탈퇴로 무효화됐거나, 회전으로 이미 교체된
     * 옛 토큰도 서명은 그대로 유효하기 때문이다. 저장된 해시와 대조해 "지금 살아 있는 세션인가"까지
     * 확인한다. 저장 레코드의 만료도 함께 보아 JWT만 위조 없이 오래된 경우를 막는다.
     */
    @Transactional(readOnly = true)
    public boolean matches(UUID userId, String rawToken) {
        String hash = TokenHasher.sha256Hex(rawToken);
        return refreshTokenRepository.findByUserId(userId)
                .filter(token -> token.getTokenHash().equals(hash))
                .filter(token -> token.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }

    /**
     * 사용자의 Refresh Token을 삭제해 무효화한다. 없으면 무시(멱등).
     * 로그아웃(API-AUTH-4)·회원탈퇴(API-USER-2) 공통 세션 종료 경로.
     */
    @Override
    @Transactional
    public void revoke(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }
}
