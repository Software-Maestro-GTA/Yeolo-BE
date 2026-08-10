package com.soma.yeolo.share.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * 공유 링크의 사용 가능 판정 (API-SHARE-2/3 §410). 만료·회수는 조회와 수락 양쪽에서 같은 규칙으로
 * 걸러져야 하므로 도메인이 소유한다.
 */
class SavedShareLinkTest {

    private static final Instant NOW = Instant.parse("2026-08-10T12:00:00Z");

    private SavedShareLink link(Instant expiresAt, Instant revokedAt) {
        return new SavedShareLink(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                expiresAt, revokedAt);
    }

    @Test
    void 만료_전이고_회수되지_않았으면_사용할_수_있다() {
        assertThat(link(NOW.plusSeconds(60), null).isUsable(NOW)).isTrue();
    }

    @Test
    void 만료_시각이_없으면_무기한이다() {
        assertThat(link(null, null).isUsable(NOW)).isTrue();
        assertThat(link(null, null).isExpired(NOW)).isFalse();
    }

    @Test
    void 만료_시각이_지나면_사용할_수_없다() {
        assertThat(link(NOW.minusSeconds(1), null).isUsable(NOW)).isFalse();
    }

    /** 경계는 만료 시각 그 순간부터 만료로 본다 — "지나야 만료"로 두면 1틱짜리 애매한 창이 남는다. */
    @Test
    void 만료_시각과_같은_순간부터_만료다() {
        assertThat(link(NOW, null).isExpired(NOW)).isTrue();
    }

    @Test
    void 회수된_링크는_만료_전이어도_사용할_수_없다() {
        SavedShareLink revoked = link(NOW.plusSeconds(3600), NOW.minusSeconds(10));

        assertThat(revoked.isRevoked()).isTrue();
        assertThat(revoked.isExpired(NOW)).isFalse();
        assertThat(revoked.isUsable(NOW)).isFalse();
    }
}
