package com.soma.yeolo.user.service;

import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link WithdrawnUserChecker}의 user 계층 어댑터 (API-USER-2).
 *
 * <p>탈퇴 여부는 {@code status} 대신 {@code deletedAt} 유무로 본다 — 탈퇴 시점에만 채워지는
 * 값이라 enum 컨버터를 거치지 않고 파생 쿼리로 곧장 물어볼 수 있다.
 *
 * <p>인증된 요청마다 기본키 조회가 한 번 더 붙는다. 탈퇴자가 남은 토큰으로 API를 계속 쓰는 창을
 * 닫는 대가이며, PK 인덱스 조회라 비용은 작다. 부담이 되면 Access Token 수명을 줄이거나
 * 결과를 짧게 캐시하는 쪽으로 조정한다.
 */
@Service
@RequiredArgsConstructor
public class WithdrawnUserCheckerAdapter implements WithdrawnUserChecker {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isWithdrawn(UUID userId) {
        return userRepository.existsByIdAndDeletedAtIsNotNull(userId);
    }
}
