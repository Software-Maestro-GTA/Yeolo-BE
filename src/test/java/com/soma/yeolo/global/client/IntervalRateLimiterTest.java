package com.soma.yeolo.global.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class IntervalRateLimiterTest {

    @Test
    void 첫_호출은_대기없이_통과하고_이후_호출은_최소간격만큼_대기한다() {
        long intervalMs = 60L;
        IntervalRateLimiter limiter = new IntervalRateLimiter(intervalMs);

        long start = System.nanoTime();
        limiter.acquire(); // 첫 호출: 대기 없음
        limiter.acquire(); // +약 60ms
        limiter.acquire(); // +약 60ms
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // 두 번의 간격(약 120ms) 이상 걸려야 한다. 스케줄링 오차를 감안해 하한만 검증한다.
        assertThat(elapsedMs).isGreaterThanOrEqualTo(2 * intervalMs - 15);
    }

    @Test
    void 간격이_0이면_대기하지_않는다() {
        IntervalRateLimiter limiter = new IntervalRateLimiter(0L);

        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            limiter.acquire();
        }
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMs).isLessThan(50);
    }
}
