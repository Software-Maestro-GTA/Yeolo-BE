package com.soma.yeolo.global.sse;

import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * 단위 테스트용 {@link SseHeartbeat} 팩토리. 실제 스케줄러를 쓰되 interval(15초)이 테스트 수행
 * 시간보다 훨씬 길어 keepalive는 실제로 발사되지 않는다 — 블로킹 호출 위임 경로만 검증된다.
 */
public final class TestSseHeartbeat {

    private TestSseHeartbeat() {
    }

    public static SseHeartbeat create() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("test-sse-heartbeat-");
        scheduler.setDaemon(true);
        scheduler.initialize();
        return new SseHeartbeat(scheduler);
    }
}
