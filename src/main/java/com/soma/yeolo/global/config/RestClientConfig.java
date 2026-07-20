package com.soma.yeolo.global.config;

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
     * AI 내부 API(SSE) 호출용. LLM 분석은 수 초~수십 초가 걸릴 수 있어 응답 타임아웃을 길게 둔다.
     * 그래도 무한 대기는 방지한다. (docs/architecture.md 5)
     */
    @Bean
    public RestClient aiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(60));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
