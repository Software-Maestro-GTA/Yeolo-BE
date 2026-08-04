package com.soma.yeolo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * SSE 중계 작업(전처리 → AI 호출 → 저장)을 요청 스레드와 분리해 실행하기 위한 워커 풀.
 */
@Configuration
public class SseTaskExecutorConfig {

    @Bean(name = "sseTaskExecutor")
    public AsyncTaskExecutor sseTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("sse-taste-");
        // 종료 시 진행 중인 파이프라인(전처리 → AI 호출 → 저장)이 유실되지 않도록 완료를 기다린다.
        // 대기 상한은 파드 grace(90s) - preStop(15s) = 75s 안쪽으로 두되 graceful shutdown과 맞춘다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }

    /**
     * SSE keepalive 전송용 스케줄러. 실제 전송은 수 밀리초라 소수 스레드로 충분하다.
     * 워커 풀과 분리해, 워커가 AI 응답을 기다리는 동안에도 heartbeat가 밀리지 않게 한다.
     */
    @Bean(name = "sseHeartbeatScheduler")
    public TaskScheduler sseHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // 전송이 TCP 백프레셔로 잠깐 막혀도 다른 스트림의 keepalive가 밀리지 않을 만큼은 둔다.
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("sse-heartbeat-");
        // 진행 중인 스트림은 워커 풀이 책임지므로, 종료 시 heartbeat는 기다리지 않고 끊는다.
        scheduler.setWaitForTasksToCompleteOnShutdown(false);
        scheduler.setDaemon(true);
        scheduler.initialize();
        return scheduler;
    }
}
