package com.soma.yeolo.myrealtrip.client;

import com.soma.yeolo.global.client.RestClients;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 파트너 API 호출용 {@link RestClient}. 공용 {@code restClient}(응답 5초)와 달리 타임아웃을 설정({@code myrealtrip.*})으로
 * 받는다 — 상품 검색은 자동완성보다 오래 걸릴 수 있고 초과 시 사용자에게 504 를 내야 하므로 값이 환경별로 조정
 * 가능해야 한다. 빈을 여기 두는 것은 global 이 도메인 설정을 import 하지 않게 하기 위해서다.
 */
@Configuration
public class MyRealTripRestClientConfig {

    @Bean
    public RestClient myRealTripRestClient(MyRealTripProperties properties) {
        return RestClients.timed(Duration.ofMillis(properties.connectTimeoutMs()),
                Duration.ofMillis(properties.readTimeoutMs()));
    }
}
