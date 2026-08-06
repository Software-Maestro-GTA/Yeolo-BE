package com.soma.yeolo.place.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.place.domain.Place;
import com.soma.yeolo.place.domain.PlaceQuery;
import org.junit.jupiter.api.Test;

class StubPlaceLookupClientTest {

    private final StubPlaceLookupClient client = new StubPlaceLookupClient();

    private PlaceQuery query(String placeName) {
        return new PlaceQuery(placeName, "nature", "대한민국", "제주");
    }

    /** 같은 장소가 코스마다 다른 placeId로 저장되지 않도록, 식별자·좌표가 결정론적이어야 한다. */
    @Test
    void 같은_장소명은_항상_같은_식별자와_좌표로_매핑된다() {
        Place first = client.lookup(query("성산일출봉")).orElseThrow();
        Place second = client.lookup(query("성산일출봉")).orElseThrow();

        assertThat(second.providerPlaceId()).isEqualTo(first.providerPlaceId());
        assertThat(second.latitude()).isEqualTo(first.latitude());
        assertThat(second.longitude()).isEqualTo(first.longitude());
    }

    @Test
    void 다른_장소명은_다른_식별자로_매핑된다() {
        Place first = client.lookup(query("성산일출봉")).orElseThrow();
        Place second = client.lookup(query("협재 해수욕장")).orElseThrow();

        assertThat(second.providerPlaceId()).isNotEqualTo(first.providerPlaceId());
    }

    @Test
    void 지도에_찍을_수_있는_좌표와_주소를_채운다() {
        Place candidate = client.lookup(query("성산일출봉")).orElseThrow();

        assertThat(candidate.latitude()).isBetween(33.2, 38.4);
        assertThat(candidate.longitude()).isBetween(126.2, 129.5);
        assertThat(candidate.address()).isEqualTo("대한민국 제주 성산일출봉");
        assertThat(candidate.photoUrls()).isEmpty();
        assertThat(candidate.openingHours()).isNotEmpty();
    }
}
