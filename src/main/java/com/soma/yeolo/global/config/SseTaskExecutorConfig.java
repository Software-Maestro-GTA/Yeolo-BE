package com.soma.yeolo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
}
