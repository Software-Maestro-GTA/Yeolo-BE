package com.soma.yeolo.place.repository;

import com.soma.yeolo.place.entity.PlaceEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 장소 Spring Data JPA 리포지토리. 포트 구현체 {@link PlaceRepositoryImpl} 내부에서만 사용하며,
 * 서비스(응용 계층)에서 직접 주입하지 않는다. (docs/architecture.md §1-2)
 */
public interface PlaceJpaRepository extends JpaRepository<PlaceEntity, UUID> {

    /** provider 식별자로 이미 저장된 장소를 찾는다(중복 저장 방지). */
    Optional<PlaceEntity> findByProviderPlaceId(String providerPlaceId);

    /**
     * 내부 식별자 → provider 식별자만 읽는다. 코스 상세 조회가 요청마다 타는 경로라 주소·사진·운영시간 등
     * 쓰지 않는 text 컬럼까지 엔티티로 적재하지 않는다.
     */
    @Query("select p.id as id, p.providerPlaceId as providerPlaceId from PlaceEntity p where p.id in :ids")
    List<ProviderPlaceIdRow> findProviderPlaceIdsByIdIn(@Param("ids") Collection<UUID> ids);

    /** {@link #findProviderPlaceIdsByIdIn} 프로젝션. */
    interface ProviderPlaceIdRow {
        UUID getId();

        String getProviderPlaceId();
    }
}
