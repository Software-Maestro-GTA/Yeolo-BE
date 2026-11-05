package com.soma.yeolo.myrealtrip.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** hasMore·totalCount 규칙 (API-MRT-3/4 §상품 및 페이지 필드) — 가짜 전체 건수를 만들지 않는다. */
class ProductPageTest {

    @Test
    void 상위가_전체_건수를_주면_그대로_쓰고_페이지_곱으로_다음_페이지를_판단한다() {
        assertThat(ProductPage.of(List.of(), 1, 45L, 20).hasMore()).isTrue();     // 20 < 45
        assertThat(ProductPage.of(List.of(), 2, 45L, 20).hasMore()).isTrue();     // 40 < 45
        assertThat(ProductPage.of(List.of(), 3, 45L, 5).hasMore()).isFalse();     // 60 < 45 아님
        assertThat(ProductPage.of(List.of(), 1, 20L, 20).hasMore()).isFalse();    // 딱 한 페이지
        assertThat(ProductPage.of(List.of(), 1, 0L, 0).totalCount()).isZero();
    }

    @Test
    void 전체_건수가_없으면_null_이고_필터링_전_원본_페이지가_가득_찼을_때만_다음_페이지가_있다() {
        ProductPage full = ProductPage.of(List.of(), 1, null, 20);   // 20개 전부 걸러져도 원본 기준
        assertThat(full.totalCount()).isNull();
        assertThat(full.hasMore()).isTrue();
        assertThat(ProductPage.of(List.of(), 1, null, 19).hasMore()).isFalse();
        assertThat(ProductPage.of(List.of(), 2, null, 0).hasMore()).isFalse();   // 다음 빈 페이지에서 종료
    }
}
