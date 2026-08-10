package com.soma.yeolo.share.repository;

import com.soma.yeolo.share.domain.SavedShareLink;
import com.soma.yeolo.share.domain.ShareLink;
import com.soma.yeolo.share.entity.ShareLinkEntity;
import com.soma.yeolo.share.service.port.ShareLinkRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ShareLinkRepository} 포트의 JPA 어댑터. 도메인↔엔티티 매핑을 이 경계에 격리한다.
 * (docs/architecture.md §1-2)
 */
@Component
@RequiredArgsConstructor
class ShareLinkRepositoryImpl implements ShareLinkRepository {

    private final ShareLinkJpaRepository jpaRepository;

    @Override
    public UUID save(ShareLink link) {
        return jpaRepository.save(ShareLinkEntity.from(link)).getId();
    }

    @Override
    public Optional<SavedShareLink> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(ShareLinkEntity::toSavedShareLink);
    }

    /**
     * 회수 상태를 명시적으로 저장한다. 더티 체킹에 맡기지 않는 이유는 호출 시점에 트랜잭션이 없으면
     * 조회 결과가 준영속이라 변경이 조용히 유실되기 때문이다 — 회수 누락은 만료된 초대가 계속
     * 열리는 결과라 조용히 실패해서는 안 된다.
     */
    @Override
    public void revokeAllByCourseId(UUID courseId, Instant revokedAt) {
        List<ShareLinkEntity> links = jpaRepository.findByCourseIdAndRevokedAtIsNull(courseId);
        links.forEach(link -> link.revoke(revokedAt));
        jpaRepository.saveAll(links);
    }
}
