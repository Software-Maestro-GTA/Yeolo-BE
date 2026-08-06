package com.soma.yeolo.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.place.client.PlaceLookupClient;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import com.soma.yeolo.place.domain.SavedPlace;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceRegistrationServiceTest {

    private final FakePlaceRepository placeRepository = new FakePlaceRepository();

    private PlaceRegistrationService serviceReturning(PlaceLookupClient lookupClient) {
        return new PlaceRegistrationService(lookupClient, placeRepository);
    }

    private Place found() {
        return new Place("google:ChIJ123", "성산일출봉", "tourist_attraction",
                "제주특별자치도 서귀포시 성산읍", 33.4581, 126.9425, 4.6,
                List.of(), List.of("월요일: 07:00~20:00"));
    }

    private PlaceQuery query() {
        return new PlaceQuery("성산일출봉", "nature", "대한민국", "제주");
    }

    @Test
    void 조회_결과를_저장하고_내부_placeId를_부여한다() {
        PlaceRegistrationService service = serviceReturning(q -> Optional.of(found()));

        SavedPlace place = service.resolve(query()).orElseThrow();

        assertThat(place.placeId()).isNotNull();
        assertThat(place.placeName()).isEqualTo("성산일출봉");
        assertThat(place.latitude()).isEqualTo(33.4581);
        assertThat(place.longitude()).isEqualTo(126.9425);
        // AI가 준 분류를 유지해 코스 stop의 category와 어긋나지 않게 한다.
        assertThat(place.category()).isEqualTo("nature");
        assertThat(placeRepository.findById(place.placeId())).isPresent();
    }

    @Test
    void 같은_장소는_다시_저장하지_않고_같은_placeId를_재사용한다() {
        PlaceRegistrationService service = serviceReturning(q -> Optional.of(found()));

        SavedPlace first = service.resolve(query()).orElseThrow();
        SavedPlace second = service.resolve(query()).orElseThrow();

        assertThat(second.placeId()).isEqualTo(first.placeId());
        assertThat(placeRepository.saveCount).isEqualTo(1);
    }

    @Test
    void provider가_장소를_찾지_못하면_빈_값을_돌려준다() {
        PlaceRegistrationService service = serviceReturning(q -> Optional.empty());

        assertThat(service.resolve(query())).isEmpty();
        assertThat(placeRepository.saveCount).isZero();
    }
}
