package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.domain.UserStatus;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import com.soma.yeolo.user.service.port.RefreshTokenRevoker;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserWithdrawalServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRevoker refreshTokenRevoker;

    @InjectMocks
    private UserWithdrawalService userWithdrawalService;

    @Test
    void 탈퇴하면_개인정보를_파기하고_세션을_무효화한다() {
        UUID userId = UUID.randomUUID();
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "u@gmail.com", "홍길동", "http://img");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userWithdrawalService.withdraw(userId);

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
        // 개인정보 영구 파기
        assertThat(user.getEmail()).isNull();
        assertThat(user.getDisplayName()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        // 세션 무효화
        verify(refreshTokenRevoker).revoke(userId);
    }

    @Test
    void 사용자가_없으면_USER_NOT_FOUND_예외를_던지고_세션을_건드리지_않는다() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userWithdrawalService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(refreshTokenRevoker, never()).revoke(userId);
    }
}
