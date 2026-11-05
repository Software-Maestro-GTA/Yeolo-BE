package com.soma.yeolo.myrealtrip.service;

import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.RateLimitExceededException;
import com.soma.yeolo.myrealtrip.client.MyRealTripProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 마이리얼트립 API 의 사용자별 요청 제한 (API-MRT-1~7 §작성 상태 "Server 에서 사용자별 요청 제한을 적용한다").
 *
 * <p>사용자(userId)마다 고정 창(fixed window)을 둔다 — 창 안에서 {@code maxRequests}를 넘으면 429 이고
 * {@code Retry-After}는 창이 끝나기까지 남은 초다. 주 목적은 파트너 호출 전에 거는 것(파트너 쿼터·요금 보호)이고,
 * 파트너를 부르지 않는 쿠폰 목록(API-MRT-7)도 명세 결정(2026-11-05, MRT-7 Error Codes 에 429 추가)에 따라 같은 한도를
 * 공유한다 — 그래서 쿠폰 조회도 한도 1을 쓴다. 수치를 파트너 쿼터에서 역산할 때 이 몫을 감안한다. 메모리 안에서만 센다. 인스턴스가 여러 개면 인스턴스마다 따로 세어 실제 한도가 인스턴스 수만큼 느슨해지는데,
 * 지금은 파드 하나이고 한도도 남용 차단용 여유값이라 공유 저장소를 두지 않았다.
 *
 * <p>만료된 창은 호출 때마다 지우지 않는다. 항목 수가 {@link #PURGE_THRESHOLD}를 넘었고 마지막 정리로부터 창 하나가
 * 지났을 때만 훑어 지운다 — 사용자 수만큼만 자라고, 자동완성 호출 경로에 전체 순회가 창마다 최대 한 번만 붙는다.
 * 한도를 넘긴 호출도 정리를 거치므로 초과 사용자가 계속 두드려도 정리가 막히지 않는다.
 */
@Component
public class MyRealTripRateLimiter {

    static final int PURGE_THRESHOLD = 10_000;

    private final int maxRequests;
    private final Duration window;
    private final Clock clock;
    private final int purgeThreshold;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastPurge = new AtomicReference<>(Instant.EPOCH);

    /**
     * 시계는 {@code ClockConfig} 빈이 아니라 항상 흐르는 시스템 시계다 — 그 빈은 날짜 규칙을 고정하려고 테스트가
     * {@code Clock.fixed}로 바꿔 끼우는데, 고정된 시계에서는 창이 끝나지 않아 한도가 영원히 닫힌다. 창 경계 검증은
     * 패키지 생성자에 가변 시계를 넘겨 한다({@code MyRealTripRateLimiterTest}).
     */
    @Autowired
    public MyRealTripRateLimiter(MyRealTripProperties properties) {
        this(properties.rateLimitMaxRequests(), properties.rateLimitWindow(), Clock.systemUTC(), PURGE_THRESHOLD);
    }

    MyRealTripRateLimiter(int maxRequests, Duration window, Clock clock) {
        this(maxRequests, window, clock, PURGE_THRESHOLD);
    }

    MyRealTripRateLimiter(int maxRequests, Duration window, Clock clock, int purgeThreshold) {
        if (maxRequests <= 0) {
            throw new IllegalArgumentException("maxRequests must be positive: " + maxRequests);
        }
        if (window == null || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("window must be positive: " + window);
        }
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
        this.purgeThreshold = purgeThreshold;
    }

    /** 현재 보관 중인 사용자 창 수(테스트용). */
    int trackedUsers() {
        return windows.size();
    }

    /**
     * 요청 하나를 센다. 한도를 넘으면 {@link RateLimitExceededException}(429)이며 창이 끝날 때까지 남은 초를 담는다.
     * 넘은 요청도 세어 두므로 한도 초과 중 계속 두드려도 창이 끝나기 전에는 열리지 않는다.
     */
    public void acquire(UUID userId) {
        Instant now = clock.instant();
        purgeIfDue(now);
        Window current = windows.compute(userId, (id, existing) ->
                existing == null || !existing.contains(now, window) ? new Window(now, 1) : existing.increment());
        if (current.count() > maxRequests) {
            // 올림 — 내림이면 헤더대로 기다린 클라이언트가 창 끝 직전에 다시 429 를 받는다.
            long remainingMillis = Duration.between(now, current.start().plus(window)).toMillis();
            long retryAfter = Math.max(1, (remainingMillis + 999) / 1000);
            throw new RateLimitExceededException(ErrorCode.MYREALTRIP_RATE_LIMITED, retryAfter);
        }
    }

    private void purgeIfDue(Instant now) {
        if (windows.size() <= purgeThreshold) {
            return;
        }
        Instant last = lastPurge.get();
        if (now.isBefore(last.plus(window)) || !lastPurge.compareAndSet(last, now)) {
            return;   // 아직 창이 안 지났거나 다른 스레드가 방금 정리를 맡았다
        }
        windows.entrySet().removeIf(entry -> !entry.getValue().contains(now, window));
    }

    private record Window(Instant start, int count) {

        boolean contains(Instant now, Duration length) {
            return now.isBefore(start.plus(length));
        }

        Window increment() {
            return new Window(start, count + 1);
        }
    }
}
