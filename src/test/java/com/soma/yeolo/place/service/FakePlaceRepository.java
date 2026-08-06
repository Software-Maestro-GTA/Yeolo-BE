package com.soma.yeolo.place.service;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.service.port.PlaceRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 장소 영속 포트의 인메모리 fake. provider 식별자 기준 중복 저장 방지까지 실제 어댑터와 같게 흉내낸다.
 * (docs/architecture.md §8 — 영속 포트는 손수 짠 fake로 대체한다)
 */
class FakePlaceRepository implements PlaceRepository {

    private final Map<UUID, SavedPlace> byId = new LinkedHashMap<>();
    private final Map<String, UUID> idByProviderPlaceId = new LinkedHashMap<>();

    /** 저장 호출 횟수 — 같은 장소를 두 번 저장하지 않는지 검증하는 데 쓴다. */
    int saveCount;

    @Override
    public SavedPlace saveIfAbsent(Place place) {
        UUID existingId = idByProviderPlaceId.get(place.providerPlaceId());
        if (existingId != null) {
            return byId.get(existingId);
        }
        saveCount++;
        UUID placeId = UUID.randomUUID();
        SavedPlace saved = new SavedPlace(placeId, place.placeName(), place.category(),
                place.address(), place.latitude(), place.longitude(), place.rating(),
                place.photoUrls(), place.openingHours());
        byId.put(placeId, saved);
        idByProviderPlaceId.put(place.providerPlaceId(), placeId);
        return saved;
    }

    @Override
    public Optional<SavedPlace> findById(UUID placeId) {
        return Optional.ofNullable(byId.get(placeId));
    }
}
