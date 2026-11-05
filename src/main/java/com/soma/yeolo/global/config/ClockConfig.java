package com.soma.yeolo.global.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * "지금"을 주입 가능하게 하는 시계 빈. 날짜 규칙이 있는 서비스(마이리얼트립 과거 체크인·출국일·쿠폰 기간)가 이것을 받아
 * 테스트에서 {@code Clock.fixed}로 바꿔 끼운다. 시간대는 UTC 로 두고, 기준일의 시간대는 각 규칙이 정한다
 * ({@code MyRealTripRequests.today} = Asia/Seoul) — 시계의 존에 의미를 두면 바꿔 낄 때 규칙이 조용히 달라진다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
