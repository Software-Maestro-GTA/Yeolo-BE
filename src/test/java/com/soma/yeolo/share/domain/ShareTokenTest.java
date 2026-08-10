package com.soma.yeolo.share.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 초대 식별자 (DOM-6 §보안 및 운영 정책). 추측 불가능해야 하고, URL 경로에 그대로 실려야 하며,
 * DB에는 평문이 아니라 해시로만 남아야 한다.
 */
class ShareTokenTest {

    @Test
    void 발급된_토큰은_매번_다르다() {
        Set<String> issued = new HashSet<>();
        IntStream.range(0, 100).forEach(i -> issued.add(ShareToken.issue().value()));

        assertThat(issued).hasSize(100);
    }

    /** {@code +}·{@code /}·{@code =}가 섞이면 경로 파라미터에서 인코딩 문제가 생긴다. */
    @Test
    void 토큰은_URL에_그대로_실을_수_있는_문자만_쓴다() {
        IntStream.range(0, 100).forEach(i ->
                assertThat(ShareToken.issue().value()).matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void 같은_토큰은_같은_해시로_조회된다() {
        ShareToken token = ShareToken.issue();

        assertThat(ShareToken.of(token.value()).hash()).isEqualTo(token.hash());
    }

    @Test
    void 해시에는_평문이_남지_않는다() {
        ShareToken token = ShareToken.issue();

        assertThat(token.hash()).doesNotContain(token.value()).hasSize(64);
    }
}
