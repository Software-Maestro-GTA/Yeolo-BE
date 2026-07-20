package com.soma.yeolo.tasteprofile.repository;

import com.soma.yeolo.tasteprofile.domain.SavedTasteProfile;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.entity.TasteProfileEntity;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TasteProfileRepository} 포트의 JPA 어댑터. Spring Data {@link TasteProfileJpaRepository}에
 * 위임해 순수 도메인을 {@link TasteProfileEntity}로 저장하고, 조회 시 엔티티를
 * {@link SavedTasteProfile}로 되돌려준다. 도메인↔엔티티 매핑을 이 경계에 격리한다.
 * (docs/architecture.md §1-2)
 */
@Component
@RequiredArgsConstructor
class TasteProfileRepositoryImpl implements TasteProfileRepository {

    private final TasteProfileJpaRepository jpaRepository;

    @Override
    public UUID save(TasteProfile profile) {
        return jpaRepository.save(TasteProfileEntity.from(profile)).getId();
    }

    @Override
    public Optional<SavedTasteProfile> findLatestByUserId(UUID userId) {
        return jpaRepository.findFirstByUserIdOrderByUpdatedAtDesc(userId)
                .map(TasteProfileEntity::toSavedProfile);
    }
}
