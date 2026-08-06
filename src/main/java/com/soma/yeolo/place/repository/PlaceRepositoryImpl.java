package com.soma.yeolo.place.repository;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.entity.PlaceEntity;
import com.soma.yeolo.place.service.port.PlaceRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * {@link PlaceRepository} 포트의 JPA 어댑터. Spring Data {@link PlaceJpaRepository}에 위임해
 * 순수 도메인을 {@link PlaceEntity}로 저장하고, 도메인↔엔티티 매핑을 이 경계에 격리한다.
 * (docs/architecture.md §1-2)
 */
@Component
@RequiredArgsConstructor
class PlaceRepositoryImpl implements PlaceRepository {

    private final PlaceJpaRepository jpaRepository;

    @Override
    public SavedPlace saveIfAbsent(Place place) {
        Optional<PlaceEntity> existing = jpaRepository.findByProviderPlaceId(place.providerPlaceId());
        if (existing.isPresent()) {
            return existing.get().toSavedPlace();
        }
        try {
            return jpaRepository.save(PlaceEntity.from(place)).toSavedPlace();
        } catch (DataIntegrityViolationException e) {
            // 같은 장소를 동시에 저장하려 한 경합(provider_place_id unique 위반).
            // 먼저 커밋된 행이 정답이므로 그것을 다시 읽어 돌려준다.
            return jpaRepository.findByProviderPlaceId(place.providerPlaceId())
                    .map(PlaceEntity::toSavedPlace)
                    .orElseThrow(() -> e);
        }
    }

    @Override
    public Optional<SavedPlace> findById(UUID placeId) {
        return jpaRepository.findById(placeId).map(PlaceEntity::toSavedPlace);
    }
}
