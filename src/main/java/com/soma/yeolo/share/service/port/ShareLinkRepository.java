package com.soma.yeolo.share.service.port;

import com.soma.yeolo.share.domain.SavedShareLink;
import com.soma.yeolo.share.domain.ShareLink;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * 공유 링크 영속 포트 (API-SHARE-1/2/3). 서비스가 소유하는 아웃바운드 인터페이스로 순수 도메인만
 * 주고받으며, JPA 세부는 어댑터 {@code ShareLinkRepositoryImpl}이 담당한다
 * (docs/architecture.md §1-2).
 */
public interface ShareLinkRepository {

    /**
     * 공유 링크를 저장하고 부여된 식별자를 반환한다.
     *
     * @return 저장된 링크의 id
     */
    UUID save(ShareLink link);

    /** 초대 식별자의 해시로 링크를 조회한다. 없으면 빈 값(404). 만료·회수 판정은 호출자가 한다. */
    Optional<SavedShareLink> findByTokenHash(String tokenHash);

    /** 코스의 모든 공유 링크를 회수한다. 이미 회수된 링크는 시각을 유지한다(멱등). */
    void revokeAllByCourseId(UUID courseId, Instant revokedAt);
}
