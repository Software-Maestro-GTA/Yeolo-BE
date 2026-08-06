package com.soma.yeolo.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceTest {

    private Place found(String category) {
        return new Place("google:ChIJ123", "성산일출봉", category,
                "제주특별자치도 서귀포시 성산읍", 33.4581, 126.9425, 4.6,
                List.of(), List.of("월요일: 07:00~20:00"));
    }

    @Test
    void 조회_결과를_그대로_유지한다() {
        Place place = found("tourist_attraction").withCategory(null);

        assertThat(place.providerPlaceId()).isEqualTo("google:ChIJ123");
        assertThat(place.placeName()).isEqualTo("성산일출봉");
        assertThat(place.category()).isEqualTo("tourist_attraction");
        assertThat(place.address()).isEqualTo("제주특별자치도 서귀포시 성산읍");
        assertThat(place.latitude()).isEqualTo(33.4581);
        assertThat(place.longitude()).isEqualTo(126.9425);
        assertThat(place.rating()).isEqualTo(4.6);
        assertThat(place.openingHours()).containsExactly("월요일: 07:00~20:00");
    }

    @Test
    void AI가_준_분류가_있으면_provider_분류보다_우선한다() {
        Place place = found("tourist_attraction").withCategory("nature");

        assertThat(place.category()).isEqualTo("nature");
        // 나머지 값은 그대로 유지된다.
        assertThat(place.providerPlaceId()).isEqualTo("google:ChIJ123");
        assertThat(place.latitude()).isEqualTo(33.4581);
    }

    @Test
    void AI_분류가_비어있으면_provider_분류를_유지한다() {
        assertThat(found("tourist_attraction").withCategory("  ").category())
                .isEqualTo("tourist_attraction");
    }

    @Test
    void 사진과_운영시간이_없으면_빈_목록으로_정규화한다() {
        Place place = new Place("osm:N1", "협재 해수욕장", "beach", null,
                33.3948, 126.2396, null, null, null);

        assertThat(place.photoUrls()).isEmpty();
        assertThat(place.openingHours()).isEmpty();
        assertThat(place.rating()).isNull();
        assertThat(place.address()).isNull();
    }

    @Test
    void provider_식별자나_장소명이_없으면_만들_수_없다() {
        assertThatThrownBy(() -> new Place(" ", "협재 해수욕장", null, null,
                33.3948, 126.2396, null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new Place("osm:N1", null, null, null,
                33.3948, 126.2396, null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
