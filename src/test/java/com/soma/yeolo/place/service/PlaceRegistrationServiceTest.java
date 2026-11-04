package com.soma.yeolo.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceRegistrationServiceTest {

    private final FakePlaceRepository placeRepository = new FakePlaceRepository();
    private final PlaceRegistrationService service = new PlaceRegistrationService(placeRepository);

    private Place found() {
        return new Place("google:ChIJ123", "성산일출봉", "Seongsan Ilchulbong", "tourist_attraction",
                "제주특별자치도 서귀포시 성산읍", 33.4581, 126.9425, 4.6,
                "https://cdn.example.com/seongsan.jpg", List.of("월요일: 07:00~20:00"));
    }

    private Place placeWithProviderId(String providerPlaceId) {
        return new Place(providerPlaceId, "장소 " + providerPlaceId, null, "nature", null,
                33.4581, 126.9425, null, null, List.of());
    }

    // ===== AI가 준 장소를 그대로 등록하는 경로 (API-AI-2) =====

    @Test
    void 이미_확보된_장소를_등록하고_내부_placeId를_부여한다() {
        SavedPlace place = service.register(found());

        assertThat(place.placeId()).isNotNull();
        assertThat(place.placeName()).isEqualTo("성산일출봉");
        assertThat(place.placeEngName()).isEqualTo("Seongsan Ilchulbong");
        assertThat(place.latitude()).isEqualTo(33.4581);
        assertThat(place.longitude()).isEqualTo(126.9425);
        assertThat(place.photoUrl()).isEqualTo("https://cdn.example.com/seongsan.jpg");
        assertThat(placeRepository.findById(place.placeId())).isPresent();
    }

    @Test
    void 같은_장소를_다시_저장하지_않고_같은_placeId를_재사용한다() {
        SavedPlace first = service.register(found());
        SavedPlace second = service.register(found());

        assertThat(second.placeId()).isEqualTo(first.placeId());
        assertThat(placeRepository.saveCount).isEqualTo(1);
    }

    // ===== 내부 placeId → Google Place ID 역조회 (코스 상세 조회 API-COURSE-2) =====

    @Test
    void 내부_placeId를_Google_Place_ID로_되돌리고_Google_형식이_아닌_장소는_뺀다() {
        SavedPlace google = service.register(placeWithProviderId("ChIJN1t_tDeuEmsRUsoyG83frY4"));
        SavedPlace osm = service.register(placeWithProviderId("osm:place12345"));
        SavedPlace stub = service.register(placeWithProviderId("stub:1a2b3c"));
        UUID unknown = UUID.randomUUID();

        Map<UUID, String> ids = service.findGooglePlaceIds(
                List.of(google.placeId(), osm.placeId(), stub.placeId(), unknown));

        assertThat(ids).containsExactly(Map.entry(google.placeId(), "ChIJN1t_tDeuEmsRUsoyG83frY4"));
    }

    /** Places API (New) 리소스 이름 형태로 들어온 장소는 접두사 없는 같은 장소와 한 행으로 모이고, Google Place ID 로 되돌아간다. */
    @Test
    void places_접두사가_붙은_식별자는_같은_장소로_합쳐지고_벗겨서_되돌린다() {
        SavedPlace prefixed = service.register(placeWithProviderId("places/ChIJN1t_tDeuEmsRUsoyG83frY4"));
        SavedPlace bare = service.register(placeWithProviderId("ChIJN1t_tDeuEmsRUsoyG83frY4"));

        assertThat(bare.placeId()).isEqualTo(prefixed.placeId());
        assertThat(placeRepository.saveCount).isEqualTo(1);
        assertThat(service.findGooglePlaceIds(List.of(prefixed.placeId())))
                .containsExactly(Map.entry(prefixed.placeId(), "ChIJN1t_tDeuEmsRUsoyG83frY4"));
    }
}
