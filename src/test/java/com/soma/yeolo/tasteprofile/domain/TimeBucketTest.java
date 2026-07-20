package com.soma.yeolo.tasteprofile.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeBucketTest {

    @ParameterizedTest
    @CsvSource({
            "0, DAWN", "4, DAWN",
            "5, MORNING", "11, MORNING",
            "12, AFTERNOON", "16, AFTERNOON",
            "17, EVENING", "20, EVENING",
            "21, NIGHT", "23, NIGHT"
    })
    void 시각을_DOM5_경계값대로_시간대로_변환한다(int hour, TimeBucket expected) {
        assertThat(TimeBucket.fromHour(hour)).isEqualTo(expected);
    }

    @Test
    void 시각_범위를_벗어나면_예외를_던진다() {
        assertThatThrownBy(() -> TimeBucket.fromHour(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TimeBucket.fromHour(24)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 저장값은_명세_문자열을_따른다() {
        assertThat(TimeBucket.DAWN.getValue()).isEqualTo("dawn");
        assertThat(TimeBucket.AFTERNOON.getValue()).isEqualTo("afternoon");
        assertThat(TimeBucket.NIGHT.getValue()).isEqualTo("night");
    }
}
