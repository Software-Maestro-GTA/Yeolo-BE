package com.soma.yeolo.tasteprofile.repository;

import com.soma.yeolo.tasteprofile.entity.TasteProfileEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 성향 프로필 Spring Data JPA 리포지토리. 포트 구현체 {@link TasteProfileRepositoryImpl}
 * 내부에서만 사용하며, 서비스(응용 계층)에서 직접 주입하지 않는다. (docs/architecture.md §1-2)
 */
public interface TasteProfileJpaRepository extends JpaRepository<TasteProfileEntity, UUID> {

    /** 사용자의 최신 성향 프로필(갱신 시각 내림차순 첫 건)을 조회한다. (API-FB-8) */
    Optional<TasteProfileEntity> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);
}
