package com.soma.yeolo.global.sse;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * SSE 파이프라인이 도는 동안 연결에 주기적으로 keepalive를 흘려보낸다.
 *
 * <p>SSE 파이프라인에는 응답 바이트가 한 번도 나가지 않는 긴 블로킹 구간이 여럿 있다 — AI 호출은
 * 수십 초가 걸리고, Reverse Geocode(이미지당)·장소 정규화(방문지당)는 {@code IntervalRateLimiter}로
 * 호출 간 최소 간격(기본 1초)을 두고 직렬 반복한다. 그동안 앞단 프록시는 연결을 idle로 보고 끊고
 * (ALB·NGINX Ingress 모두 기본 60초), 서버는 다음 전송 시점에야 {@code Broken pipe}로 이를
 * 알아차린다 — 그때는 이미 결과를 보낼 곳이 없다.
 *
 * <p>그래서 heartbeat는 특정 단계가 아니라 <b>파이프라인 전체</b>를 덮는다. 서버 SSE 타임아웃을
 * 늘리는 것으로는 어느 쪽도 해결되지 않는다(끊는 주체가 서버가 아니다).
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
     * keepalive 전송을 시작한다. try-with-resources로 감싸 파이프라인 전 구간을 덮는다.
     *
     * <pre>{@code
     * try (SseHeartbeat.Handle beat = heartbeat.start(stream)) {
     *     ... 전처리 → AI 호출 → 후처리 → 저장 ...
     * }
     * }</pre>
     */
    public Handle start(SseStream stream) {
        ScheduledFuture<?> beat = scheduler.scheduleAtFixedRate(
                stream::sendHeartbeat, Instant.now().plus(INTERVAL), INTERVAL);
        return () -> beat.cancel(false);
    }

    /** keepalive 중단 핸들. 실패할 일이 없으므로 {@code close()}는 예외를 던지지 않는다. */
    @FunctionalInterface
    public interface Handle extends AutoCloseable {
        @Override
        void close();
    }
}
