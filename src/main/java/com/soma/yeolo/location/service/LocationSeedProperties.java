package com.soma.yeolo.location.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기준 데이터셋 적재 설정 (API-LOC-1 / API-LOC-2).
 *
 * @param enabled       부팅 시 적재 여부. 테스트처럼 기준 데이터가 필요 없는 컨텍스트에서 끈다
 * @param countriesPath 국가 데이터셋 클래스패스 경로
 * @param citiesPath    도시 데이터셋 클래스패스 경로
 */
@ConfigurationProperties(prefix = "location.seed")
public record LocationSeedProperties(boolean enabled, String countriesPath, String citiesPath) {
}
