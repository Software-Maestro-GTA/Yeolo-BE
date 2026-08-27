package com.soma.yeolo.share.repository;

import com.soma.yeolo.share.entity.ShareLinkEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 공유 링크 Spring Data JPA 리포지토리. 포트 구현체 {@link ShareLinkRepositoryImpl} 내부에서만
 * 사용한다. (docs/architecture.md §1-2)
 */
public interface ShareLinkJpaRepository extends JpaRepository<ShareLinkEntity, UUID> {

    Optional<ShareLinkEntity> findByTokenHash(String tokenHash);

    /** 회수되지 않은 링크만 가져온다 — 이미 회수된 링크의 시각을 덮어쓰지 않기 위함이다. */
    List<ShareLinkEntity> findByCourseIdAndRevokedAtIsNull(UUID courseId);
}
