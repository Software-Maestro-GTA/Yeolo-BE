package com.soma.yeolo.place.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.SavedPlace;
import com.soma.yeolo.place.dto.PlaceDetailResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlaceQueryServiceTest {

    private final FakePlaceRepository placeRepository = new FakePlaceRepository();
    private final PlaceQueryService service = new PlaceQueryService(placeRepository);

    private SavedPlace given(Place place) {
        return placeRepository.saveIfAbsent(place);
    }

    @Test
    void 내부_placeId로_장소_상세를_조회한다() {
        SavedPlace saved = given(new Place("google:ChIJ123", "성산일출봉", "nature",
                "제주특별자치도 서귀포시 성산읍", 33.4581, 126.9425, 4.6,
                List.of("https://cdn.yeolo.app/p1.jpg"), List.of("월요일: 07:00~20:00")));

        PlaceDetailResponse.PlaceDetail place = service.getPlace(saved.placeId()).place();

        assertThat(place.placeId()).isEqualTo(saved.placeId().toString());
        assertThat(place.placeName()).isEqualTo("성산일출봉");
        assertThat(place.category()).isEqualTo("nature");
        assertThat(place.address()).isEqualTo("제주특별자치도 서귀포시 성산읍");
        assertThat(place.latitude()).isEqualTo(33.4581);
        assertThat(place.longitude()).isEqualTo(126.9425);
        assertThat(place.rating()).isEqualTo(4.6);
        assertThat(place.photoUrls()).containsExactly("https://cdn.yeolo.app/p1.jpg");
        assertThat(place.openingHours()).containsExactly("월요일: 07:00~20:00");
    }

    @Test
    void 사진과_운영시간이_없으면_빈_배열로_평점은_null로_반환한다() {
        SavedPlace saved = given(new Place("osm:N1", "협재 해수욕장", "beach", null,
                33.3948, 126.2396, null, List.of(), List.of()));

        PlaceDetailResponse.PlaceDetail place = service.getPlace(saved.placeId()).place();

        assertThat(place.photoUrls()).isEmpty();
        assertThat(place.openingHours()).isEmpty();
        assertThat(place.rating()).isNull();
        assertThat(place.address()).isNull();
    }

    /**
     * 직렬화된 응답으로 검증한다 — FE가 실제로 받는 JSON에 Google Place ID가 없고 명세(API-PLACE-1)의
     * 필드만 있는지가 인수 기준이기 때문이다.
     */
    @Test
    void 응답_JSON에_Google_Place_ID가_노출되지_않는다() throws Exception {
        SavedPlace saved = given(new Place("google:ChIJ_SECRET", "성산일출봉", "nature",
                "제주", 33.4581, 126.9425, null, List.of(), List.of()));

        String json = new ObjectMapper().writeValueAsString(service.getPlace(saved.placeId()));

        assertThat(json).doesNotContain("ChIJ_SECRET").doesNotContain("providerPlaceId");
        JsonNode place = new ObjectMapper().readTree(json).path("place");
        assertThat(place.fieldNames()).toIterable().containsExactlyInAnyOrder(
                "placeId", "placeName", "category", "address",
                "latitude", "longitude", "rating", "photoUrls", "openingHours");
        assertThat(place.path("placeId").asText()).isEqualTo(saved.placeId().toString());
    }

    @Test
    void 없는_장소면_404로_노출한다() {
        assertThatThrownBy(() -> service.getPlace(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PLACE_NOT_FOUND);
    }
}
