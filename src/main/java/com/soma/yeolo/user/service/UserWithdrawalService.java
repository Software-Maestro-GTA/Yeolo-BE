package com.soma.yeolo.user.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import com.soma.yeolo.user.service.port.RefreshTokenRevoker;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원탈퇴 오케스트레이션 (API-FB-12): 계정을 소프트 삭제(status=deleted)하고,
 * 발급된 Refresh Token을 무효화해 세션을 종료한다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;

    /**
     * 사용자를 탈퇴 처리한다. 사용자가 없으면 {@link ErrorCode#USER_NOT_FOUND}(404).
     * 소프트 삭제(상태 전환)이므로 세션 무효화까지 한 트랜잭션으로 처리한다.
     */
    @Transactional
    public void withdraw(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.withdraw();
        refreshTokenRevoker.revoke(userId);
    }
}
