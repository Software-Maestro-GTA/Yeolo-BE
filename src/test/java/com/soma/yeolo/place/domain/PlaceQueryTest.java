package com.soma.yeolo.place.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlaceQueryTest {

    @Test
    void 검색어는_장소명_도시_국가_순으로_조합된다() {
        PlaceQuery query = new PlaceQuery("성산일출봉", "nature", "대한민국", "제주");

        assertThat(query.searchText()).isEqualTo("성산일출봉, 제주, 대한민국");
    }

    @Test
    void 목적지가_비어있으면_장소명만으로_검색한다() {
        assertThat(new PlaceQuery("성산일출봉", null, null, " ").searchText())
                .isEqualTo("성산일출봉");
    }

    @Test
    void 장소명이_없으면_질의를_만들_수_없다() {
        assertThatThrownBy(() -> new PlaceQuery(" ", null, "대한민국", "제주"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
