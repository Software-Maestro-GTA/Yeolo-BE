package com.soma.yeolo.place.repository;

import com.soma.yeolo.place.entity.PlaceEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 장소 Spring Data JPA 리포지토리. 포트 구현체 {@link PlaceRepositoryImpl} 내부에서만 사용하며,
 * 서비스(응용 계층)에서 직접 주입하지 않는다. (docs/architecture.md §1-2)
 */
public interface PlaceJpaRepository extends JpaRepository<PlaceEntity, UUID> {

    /** provider 식별자로 이미 저장된 장소를 찾는다(중복 저장 방지). */
    Optional<PlaceEntity> findByProviderPlaceId(String providerPlaceId);
}
