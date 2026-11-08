package com.soma.yeolo.myrealtrip.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 잘못 조립된 항공 검색 조건이 파트너까지 가지 않도록 생성 시점에 막는다(사용자 입력의 400 은 서비스가 먼저 낸다). */
class FlightSearchTest {

    private static final LocalDate DEP = LocalDate.parse("2026-12-10");
    private static final LocalDate ARR = LocalDate.parse("2026-12-12");

    @Test
    void 공항_코드는_대문자_3자리이고_서로_달라야_하며_귀국일은_출국일과_같거나_이후_성인은_1명_이상이다() {
        assertThatCode(() -> new FlightSearch("ICN", "NRT", DEP, ARR, 1)).doesNotThrowAnyException();
        assertThatCode(() -> new FlightSearch("ICN", "NRT", DEP, DEP, 1)).doesNotThrowAnyException();   // 당일 왕복
        assertThatThrownBy(() -> new FlightSearch("icn", "NRT", DEP, ARR, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSearch("ICN", "NRTX", DEP, ARR, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSearch("ICN", "ICN", DEP, ARR, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSearch("ICN", "NRT", ARR, DEP, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSearch("ICN", "NRT", null, ARR, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FlightSearch("ICN", "NRT", DEP, ARR, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
