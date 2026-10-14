package com.soma.yeolo.course.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TripConditionTest {

    private TripCondition condition(BigDecimal budget) {
        return new TripCondition("일본", "오사카", LocalDate.of(2026, 9, 1), 3, budget);
    }

    @Test
    void 양수_예산이면_만들어진다() {
        assertThatCode(() -> condition(BigDecimal.valueOf(500_000))).doesNotThrowAnyException();
        assertThatCode(() -> condition(new BigDecimal("0.01"))).doesNotThrowAnyException();
    }

    /**
     * 명세 개정으로 {@code BudgetType} 열거형이 사라지며 도메인 경계의 검증도 함께 사라질 뻔했다.
     * 컨트롤러를 거치지 않는 호출자가 0·음수를 AI로 흘려보내면 AI 400이 사용자에게 500으로 보인다.
     */
    @Test
    void 예산이_양수가_아니면_도메인_경계에서_거부한다() {
        assertThatThrownBy(() -> condition(BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> condition(BigDecimal.valueOf(-1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> condition(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
