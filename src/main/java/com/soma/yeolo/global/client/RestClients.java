package com.soma.yeolo.global.client;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 타임아웃을 명시한 {@link RestClient} 생성 (docs/architecture.md 5: 무한 대기 금지). 공용·AI·파트너 클라이언트
 * 빈이 같은 본문을 반복하지 않도록 한 곳에 둔다. 도메인 설정 클래스가 이것을 쓰므로 의존 방향은 도메인 → global 이다.
 */
public final class RestClients {

    private RestClients() {
    }

    public static RestClient timed(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
