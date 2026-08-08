package com.soma.yeolo.tasteprofile.service;

import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import com.soma.yeolo.user.service.port.TasteProfileEraser;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원탈퇴 시 취향 프로필을 파기하는 어댑터 (API-USER-2). user 계층이 소유한 출력 포트
 * {@link TasteProfileEraser}를 tasteprofile 계층에서 구현해, 두 도메인이 서로의 구현이 아니라
 * 인터페이스로만 만나게 한다. ({@code RefreshTokenService}가 {@code RefreshTokenRevoker}를
 * 구현하는 것과 같은 방식, docs/architecture.md §1-2)
 */
@Service
@RequiredArgsConstructor
public class TasteProfileEraseService implements TasteProfileEraser {

    private final TasteProfileRepository tasteProfileRepository;

    /**
     * 사용자의 취향 프로필을 모두 삭제한다. 호출자(탈퇴)의 트랜잭션에 참여해, 계정 파기와 함께
     * 커밋되거나 함께 롤백된다 — 계정만 지워지고 취향 데이터가 남는 중간 상태를 만들지 않는다.
     */
    @Override
    @Transactional
    public void eraseAll(UUID userId) {
        tasteProfileRepository.deleteByUserId(userId);
    }
}
