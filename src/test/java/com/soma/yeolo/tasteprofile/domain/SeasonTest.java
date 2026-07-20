package com.soma.yeolo.tasteprofile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SeasonTest {

    @ParameterizedTest
    @CsvSource({
            "3, SPRING", "5, SPRING",
            "6, SUMMER", "8, SUMMER",
            "9, AUTUMN", "11, AUTUMN",
            "12, WINTER", "1, WINTER", "2, WINTER"
    })
    void 월을_DOM5_기준대로_계절로_변환한다(int month, Season expected) {
        assertThat(Season.fromMonth(month)).isEqualTo(expected);
    }

    @Test
    void 월_범위를_벗어나면_예외를_던진다() {
        assertThatThrownBy(() -> Season.fromMonth(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Season.fromMonth(13)).isInstanceOf(IllegalArgumentException.class);
    }
}
