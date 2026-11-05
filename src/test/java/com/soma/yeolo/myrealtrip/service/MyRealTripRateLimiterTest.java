package com.soma.yeolo.myrealtrip.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 사용자별 고정 창 요청 제한 — 한도·창 경계·Retry-After·사용자 분리를 시계를 고정해 검증한다. */
class MyRealTripRateLimiterTest {

    private static final Instant T0 = Instant.parse("2026-11-05T09:00:00Z");

    private final MutableClock clock = new MutableClock(T0);
    private final MyRealTripRateLimiter limiter = new MyRealTripRateLimiter(3, Duration.ofMinutes(1), clock);
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void 창_안에서는_한도까지_통과하고_넘으면_429와_창이_끝날_때까지_남은_초를_준다() {
        assertThatCode(() -> {
            limiter.acquire(alice);
            limiter.acquire(alice);
            limiter.acquire(alice);
        }).doesNotThrowAnyException();

        clock.advance(Duration.ofSeconds(20));
        assertThatThrownBy(() -> limiter.acquire(alice))
                .isInstanceOfSatisfying(RateLimitExceededException.class, e -> {
                    assertThat(e.getErrorCode()).isEqualTo(ErrorCode.MYREALTRIP_RATE_LIMITED);
                    assertThat(e.getRetryAfterSeconds()).isEqualTo(40L);
                });
    }

    @Test
    void Retry_After는_올림이라_헤더대로_기다리면_창이_끝나_있다() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(alice);
        }
        clock.advance(Duration.ofMillis(20_200));   // 남은 39.8초 → 40
        assertThatThrownBy(() -> limiter.acquire(alice))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(40L));
        clock.advance(Duration.ofSeconds(40));
        assertThatCode(() -> limiter.acquire(alice)).doesNotThrowAnyException();
    }

    @Test
    void 한도_초과_중_계속_두드려도_창이_끝나기_전에는_열리지_않는다() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(alice);
        }
        clock.advance(Duration.ofSeconds(59));
        assertThatThrownBy(() -> limiter.acquire(alice)).isInstanceOf(RateLimitExceededException.class);
        assertThatThrownBy(() -> limiter.acquire(alice))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1L));   // 0 이 아니라 최소 1초
    }

    @Test
    void 창이_지나면_새_창으로_다시_허용한다() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(alice);
        }
        assertThatThrownBy(() -> limiter.acquire(alice)).isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> limiter.acquire(alice)).doesNotThrowAnyException();
    }

    @Test
    void 사용자마다_따로_센다() {
        for (int i = 0; i < 3; i++) {
            limiter.acquire(alice);
        }
        assertThatThrownBy(() -> limiter.acquire(alice)).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> limiter.acquire(bob)).doesNotThrowAnyException();
    }

    @Test
    void 항목_수가_임계를_넘고_창_하나가_지나면_만료된_창만_지운다() {
        MyRealTripRateLimiter small = new MyRealTripRateLimiter(3, Duration.ofMinutes(1), clock, 2);
        UUID carol = UUID.randomUUID();
        UUID dave = UUID.randomUUID();
        UUID eve = UUID.randomUUID();
        small.acquire(alice);
        small.acquire(bob);
        small.acquire(carol);                       // 들어올 때 2 ≤ 임계 2 — 정리 없음
        assertThat(small.trackedUsers()).isEqualTo(3);

        clock.advance(Duration.ofSeconds(30));
        small.acquire(dave);                        // 3 > 2 이고 첫 정리 — 훑지만 만료된 창이 없다. 마지막 정리 = T0+30s
        assertThat(small.trackedUsers()).isEqualTo(4);

        clock.advance(Duration.ofSeconds(15));      // T0+45s
        small.acquire(eve);                         // 4 > 2 지만 정리 후 창이 안 지나 훑지 않는다
        assertThat(small.trackedUsers()).isEqualTo(5);

        clock.advance(Duration.ofSeconds(46));      // T0+91s: alice·bob·carol(T0+60)·dave(T0+90) 만료, eve(T0+105) 생존
        small.acquire(alice);                       // 정리는 compute 전에 돈다 — 한도 안이든 밖이든
        assertThat(small.trackedUsers()).isEqualTo(2);   // eve + 새 alice
    }

    @Test
    void 한도와_창은_양수여야_한다() {
        assertThatThrownBy(() -> new MyRealTripRateLimiter(0, Duration.ofMinutes(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MyRealTripRateLimiter(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** 테스트용 가변 시계. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
