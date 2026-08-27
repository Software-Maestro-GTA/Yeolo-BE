package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.client.ProfileImageStorage;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.domain.UserStatus;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import com.soma.yeolo.user.service.port.RefreshTokenRevoker;
import com.soma.yeolo.user.service.port.TasteProfileEraser;
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

    @Mock
    private TasteProfileEraser tasteProfileEraser;

    @Mock
    private ProfileImageStorage profileImageStorage;

    @InjectMocks
    private UserWithdrawalService userWithdrawalService;

    private User activeUser() {
        return User.createOAuthUser(Provider.GOOGLE, "sub-1", "u@gmail.com", "홍길동", "http://img");
    }

    @Test
    void 탈퇴하면_계정_식별정보를_파기하고_세션을_무효화한다() {
        UUID userId = UUID.randomUUID();
        User user = activeUser();
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
    void 탈퇴하면_저장소의_프로필_이미지도_파기한다() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));

        userWithdrawalService.withdraw(userId);

        // URL 컬럼만 비우면 사진 파일은 저장소에 그대로 남는다 — 실물까지 지워야 파기다.
        verify(profileImageStorage).deleteAll(userId);
    }

    @Test
    void 탈퇴하면_취향_프로필도_파기한다() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));

        userWithdrawalService.withdraw(userId);

        // 사진에서 파생된 이동 이력이므로 계정 식별정보와 함께 지운다.
        verify(tasteProfileEraser).eraseAll(userId);
    }

    @Test
    void 이미지_파기에_실패하면_예외를_전파해_탈퇴를_되돌린다() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(activeUser()));
        doThrow(new BusinessException(ErrorCode.PROFILE_IMAGE_DELETE_FAILED))
                .when(profileImageStorage).deleteAll(userId);

        // 사진이 남았는데 "탈퇴 성공"으로 응답하면 안 된다 — 500으로 드러내고 트랜잭션을 롤백한다.
        assertThatThrownBy(() -> userWithdrawalService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_IMAGE_DELETE_FAILED);
    }

    @Test
    void 사용자가_없으면_USER_NOT_FOUND_예외를_던지고_아무것도_파기하지_않는다() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userWithdrawalService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(refreshTokenRevoker, never()).revoke(userId);
        verify(tasteProfileEraser, never()).eraseAll(userId);
        verify(profileImageStorage, never()).deleteAll(userId);
    }
}
