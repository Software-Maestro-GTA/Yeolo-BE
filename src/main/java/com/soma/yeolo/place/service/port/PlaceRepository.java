package com.soma.yeolo.place.service.port;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import java.util.Optional;
import java.util.UUID;

/**
 * 장소 영속 포트. 서비스(응용 계층)가 소유하는 아웃바운드 인터페이스로, 순수 도메인
 * {@link Place}/{@link SavedPlace}만 주고받는다. JPA·Spring Data 등 영속성 세부는 알지 못한다(DIP).
 *
 * <p>구현체 {@code PlaceRepositoryImpl}({@code repository/})가 Spring Data {@code PlaceJpaRepository}에
 * 위임하며 도메인↔엔티티 매핑을 담당한다. (docs/architecture.md §1-2)
 */
public interface PlaceRepository {

    /**
     * provider 식별자 기준으로 이미 저장된 장소가 있으면 그것을, 없으면 새로 저장해 반환한다.
     *
     * <p>같은 장소가 여러 코스·여러 사용자에게 추천되므로 중복 저장을 막고, 저장된 행을 외부 조회
     * 결과의 캐시로 재사용한다. 동시에 같은 장소를 저장하려는 경합은 구현체가 흡수한다.
     */
    SavedPlace saveIfAbsent(Place place);

    /** 내부 식별자로 장소를 조회한다. 없으면 빈 값. (API-PLACE-1) */
    Optional<SavedPlace> findById(UUID placeId);
}
