package com.soma.yeolo.myrealtrip.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 잘못 조립된 조회 조건이 파트너까지 가지 않도록 생성 시점에 막는다(사용자 입력의 400 은 서비스가 먼저 낸다). */
class HotelSearchQueryTest {

    private static final LocalDate IN = LocalDate.parse("2026-12-10");
    private static final LocalDate OUT = LocalDate.parse("2026-12-12");

    @Test
    void 체크아웃은_체크인_이후_성인은_1명_이상_페이지는_1_이상이어야_한다() {
        assertThatCode(() -> new HotelSearchQuery(0, IN, OUT, 1, 1)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new HotelSearchQuery(1, null, OUT, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HotelSearchQuery(1, IN, null, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HotelSearchQuery(1, IN, IN, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HotelSearchQuery(1, OUT, IN, 1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HotelSearchQuery(1, IN, OUT, 0, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new HotelSearchQuery(1, IN, OUT, 1, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
