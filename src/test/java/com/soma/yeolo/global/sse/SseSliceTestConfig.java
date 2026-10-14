package com.soma.yeolo.global.sse;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;

/**
 * SSE 컨트롤러 슬라이스 테스트({@code @WebMvcTest})용 공용 빈. 컨트롤러가 요구하는
 * {@link SseProperties}와 {@code sseTaskExecutor}를 제공하며, 워커를 인라인 실행해 파이프라인
 * 시작 여부를 경합 없이 검증할 수 있게 한다. 코스 생성·취향 분석 테스트가 함께 쓴다.
 */
@TestConfiguration
public class SseSliceTestConfig {

    @Bean
    SseProperties sseProperties() {
        return new SseProperties(600_000L);
    }

    @Bean(name = "sseTaskExecutor")
    AsyncTaskExecutor sseTaskExecutor() {
        return new TaskExecutorAdapter(Runnable::run);
    }
}
