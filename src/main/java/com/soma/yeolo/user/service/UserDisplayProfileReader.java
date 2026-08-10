package com.soma.yeolo.user.service;

import com.soma.yeolo.user.domain.UserDisplayProfile;
import java.util.Optional;
import java.util.UUID;

/**
 * 타 도메인에 노출하는 사용자 표시 프로필 조회 포트 (DOM-1 → API-SHARE-2).
 *
 * <p>공유 링크 미리보기는 초대자의 이름·프로필 이미지만 필요하므로, 사용자 도메인의 저장·갱신
 * 로직이나 {@code User} 엔티티 자체를 끌어오지 않도록 읽기 전용 포트로 좁혀 노출한다. 소비자는
 * 영속 계층을 모른 채 이 포트에만 의존하므로 단위 테스트에서 람다로 대체할 수 있다
 * (docs/architecture.md §1-2 — {@code UserMbtiReader}와 같은 방식).
 */
@FunctionalInterface
public interface UserDisplayProfileReader {

    /** 사용자의 표시 프로필. 사용자가 없으면 빈 값. */
    Optional<UserDisplayProfile> findDisplayProfile(UUID userId);
}
