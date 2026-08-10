package com.soma.yeolo.course.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BudgetTypeTest {

    @Test
    void 명세_전송값으로_enum을_찾는다() {
        assertThat(BudgetType.fromValue("cost_effective")).isEqualTo(BudgetType.COST_EFFECTIVE);
        assertThat(BudgetType.fromValue("moderate")).isEqualTo(BudgetType.MODERATE);
        assertThat(BudgetType.fromValue("luxury")).isEqualTo(BudgetType.LUXURY);
    }

    @Test
    void enum은_명세_소문자_값을_그대로_노출한다() {
        assertThat(BudgetType.MODERATE.getValue()).isEqualTo("moderate");
        assertThat(BudgetType.LUXURY.getValue()).isEqualTo("luxury");
    }

    /**
     * 회귀 방지 — 이 검증이 사라진 사이 {@code standard}가 되살아나 AI가 코스 생성을 400으로
     * 반려했다. 명세에서 제거된 값은 BE 경계에서 끊어야 실패가 런타임까지 밀리지 않는다.
     */
    @Test
    void 명세_개정으로_제거된_standard는_거부된다() {
        assertThatThrownBy(() -> BudgetType.fromValue("standard"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 알_수_없는_값이면_예외() {
        assertThatThrownBy(() -> BudgetType.fromValue("premium"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
