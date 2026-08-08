package com.soma.yeolo.user.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.user.client.ProfileImageStorage;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import com.soma.yeolo.user.service.port.RefreshTokenRevoker;
import com.soma.yeolo.user.service.port.TasteProfileEraser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원탈퇴 오케스트레이션 (API-USER-2): 계정을 소프트 삭제(status=deleted)하며 개인정보를 파기한다.
 *
 * <p>파기 대상은 네 갈래다 — 계정의 식별정보(이메일·이름·프로필 이미지 URL·OAuth 식별자),
 * 저장소의 프로필 이미지 파일, 취향 프로필(사진에서 파생된 이동 이력), 그리고 세션(Refresh Token).
 * 코스 데이터는 파기하지 않고 삭제된 소유자를 참조한 채 남겨 둔다.
 *
 * <p>계정 행 자체를 지우지 않는 이유는 코스가 이 식별자를 참조하기 때문이다. 대신 사람을 특정할 수
 * 있는 값을 모두 없애고, OAuth 식별자는 {@code deleted:<id>}로 치환해 같은 계정으로 재가입하면
 * 새 사용자가 되도록 한다.
 */
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserRepository userRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final TasteProfileEraser tasteProfileEraser;
    private final ProfileImageStorage profileImageStorage;

    /**
     * 사용자를 탈퇴 처리한다. 사용자가 없으면 {@link ErrorCode#USER_NOT_FOUND}(404).
     *
     * <p>DB 파기는 한 트랜잭션으로 묶어 부분 삭제 상태를 만들지 않는다. 저장소 이미지 파기는 외부
     * 호출이라 트랜잭션에 참여할 수 없으므로 <b>커밋 전에</b> 수행하고, 실패하면 예외를 그대로
     * 던져 전체를 롤백한다 — 사진이 남았는데 "탈퇴 성공"으로 응답하는 것이 가장 나쁜 결과다.
     * 그 대가로 저장소 장애 중에는 탈퇴가 500으로 실패하며, 사용자는 재시도해야 한다(멱등).
     */
    @Transactional
    public void withdraw(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.withdraw();
        tasteProfileEraser.eraseAll(userId);
        refreshTokenRevoker.revoke(userId);
        profileImageStorage.deleteAll(userId);
    }
}
