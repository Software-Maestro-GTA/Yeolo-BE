package com.soma.yeolo.global.config;

import com.soma.yeolo.global.client.IntervalRateLimiter;
import com.soma.yeolo.global.client.NominatimProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenStreetMap(Nominatim) 공개 서버 호출 정책.
 *
 * <p>"초당 1회 이하"는 <b>호출자(IP) 단위</b> 정책이므로, 역지오코딩(DOM-5)과 장소 조회(DOM-3)가
 * 각자 리미터를 들면 합산 2req/s가 되어 정책을 어긴다. 이 호스트를 호출하는 모든 어댑터가 하나의
 * 리미터를 공유하도록 단일 빈으로 만든다 — 호출자가 늘어도 여기를 고칠 필요가 없다.
 */
@Configuration
public class NominatimConfig {

    @Bean
    public IntervalRateLimiter nominatimRateLimiter(NominatimProperties properties) {
        return new IntervalRateLimiter(properties.minIntervalMs());
    }
}
