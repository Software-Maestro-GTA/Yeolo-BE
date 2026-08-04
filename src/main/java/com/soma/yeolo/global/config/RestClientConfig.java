package com.soma.yeolo.global.config;

import com.soma.yeolo.global.client.AiClientProperties;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 API 호출용 RestClient. 무한 대기 방지를 위해 연결/응답 타임아웃을 명시한다.
 * (docs/architecture.md 5)
 */
@Configuration
public class RestClientConfig {

    /** 일반 외부 호출(Google OAuth 등)용. 짧은 응답 타임아웃. */
    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * AI 내부 API(SSE) 호출용. LLM 분석은 실측 80초대라 응답 타임아웃을 길게 둔다 — 오래 걸리는 것은
     * 정상이며 끊을 사유가 아니다. 그래도 무한 대기는 방지한다. (docs/architecture.md 5)
     *
     * <p>값은 {@code ai.internal.*}로 주입한다. 환경별로 AI 응답 속도가 다르고, 코드 상수로 박혀
     * 있으면 실측이 바뀔 때마다 재배포해야 하기 때문이다.
     */
    @Bean
    public RestClient aiRestClient(AiClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.connectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
