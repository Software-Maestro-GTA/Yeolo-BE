package com.soma.yeolo.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BudgetTypeTest {

    @Test
    void 명세_전송값으로_enum을_찾는다() {
        assertThat(BudgetType.fromValue("cost_effective")).isEqualTo(BudgetType.COST_EFFECTIVE);
        assertThat(BudgetType.fromValue("standard")).isEqualTo(BudgetType.STANDARD);
        assertThat(BudgetType.fromValue("luxury")).isEqualTo(BudgetType.LUXURY);
    }

    @Test
    void enum은_명세_소문자_값을_그대로_노출한다() {
        assertThat(BudgetType.LUXURY.getValue()).isEqualTo("luxury");
    }

    @Test
    void 알_수_없는_값이면_예외() {
        assertThatThrownBy(() -> BudgetType.fromValue("premium"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
