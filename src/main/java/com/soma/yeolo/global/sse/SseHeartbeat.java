package com.soma.yeolo.global.sse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 오래 걸리는 블로킹 구간(AI 호출) 동안 SSE 연결에 주기적으로 keepalive를 흘려보낸다.
 *
 * <p>AI 분석은 수십 초가 걸리는데 그동안 응답 바이트가 한 번도 나가지 않으면, 앞단 프록시가
 * 연결을 idle로 보고 끊는다(ALB·NGINX Ingress 모두 기본 60초). 서버는 다음 전송 시점에야
 * {@code Broken pipe}로 이를 알아차리므로, 그때는 이미 결과를 보낼 곳이 없다.
 * heartbeat는 (1) 연결을 idle에서 벗어나게 하고 (2) 진짜 클라이언트 이탈을 interval 안에
 * 감지하게 해준다. 서버 SSE 타임아웃을 늘리는 것으로는 어느 쪽도 해결되지 않는다.
 */
@Slf4j
@Component
public class SseHeartbeat {

    /** 프록시 idle timeout(통상 60초)보다 충분히 짧게 둔다. */
    private static final Duration INTERVAL = Duration.ofSeconds(15);

    private final TaskScheduler scheduler;

    public SseHeartbeat(@Qualifier("sseHeartbeatScheduler") TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * {@code blockingCall}이 끝날 때까지 heartbeat를 유지한 뒤 결과를 반환한다.
     * 호출 자체의 예외는 그대로 전파하고, heartbeat는 성공·실패와 무관하게 정리한다.
     */
    public <T> T runWithHeartbeat(SseStream stream, Supplier<T> blockingCall) {
        ScheduledFuture<?> beat = scheduler.scheduleAtFixedRate(
                stream::sendHeartbeat, Instant.now().plus(INTERVAL), INTERVAL);
        try {
            return blockingCall.get();
        } finally {
            beat.cancel(false);
        }
    }
}
