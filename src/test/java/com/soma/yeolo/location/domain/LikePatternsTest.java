package com.soma.yeolo.location.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * LIKE 패턴 조립·이스케이프를 검증한다. 이스케이프가 빠지면 {@code keyword=%} 한 글자로 전체
 * 행이 걸린다 — 조용히 잘못된 결과가 나가는 종류의 버그라 여기서 못 박는다.
 */
class LikePatternsTest {

    @Test
    void 앞부분_일치_패턴은_뒤에만_와일드카드를_붙인다() {
        assertThat(LikePatterns.prefix("서울")).isEqualTo("서울%");
    }

    @Test
    void 부분_일치_패턴은_양쪽에_와일드카드를_붙인다() {
        assertThat(LikePatterns.contains("서울")).isEqualTo("%서울%");
    }

    @Test
    void 검색어의_와일드카드는_이스케이프된다() {
        assertThat(LikePatterns.contains("100%")).isEqualTo("%100!%%");
        assertThat(LikePatterns.contains("a_b")).isEqualTo("%a!_b%");
    }

    @Test
    void 이스케이프_문자_자체도_이스케이프된다() {
        assertThat(LikePatterns.prefix("a!b")).isEqualTo("a!!b%");
    }
}
