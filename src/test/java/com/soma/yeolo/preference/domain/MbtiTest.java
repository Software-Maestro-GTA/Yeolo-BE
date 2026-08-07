package com.soma.yeolo.preference.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 순수 도메인 테스트 — MBTI 입력값 해석 규칙(API-PREF-1 인수 기준 "잘못된 MBTI 값은 400").
 */
class MbtiTest {

    @Test
    void 열여섯_유형을_모두_가진다() {
        assertThat(Mbti.values()).hasSize(16);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENFP", "enfp", "EnFp", "  ENFP  "})
    void 대소문자와_앞뒤_공백을_허용하고_대문자_유형으로_해석한다(String input) {
        assertThat(Mbti.fromValue(input)).contains(Mbti.ENFP);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ENFPP", "XXXX", "EN", "ENF P", "", "  "})
    void 열여섯_유형이_아니면_빈_값이다(String input) {
        assertThat(Mbti.fromValue(input)).isEmpty();
    }

    @Test
    void null이면_빈_값이다() {
        assertThat(Mbti.fromValue(null)).isEmpty();
    }
}
