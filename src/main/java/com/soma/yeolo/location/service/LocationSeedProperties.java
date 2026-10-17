package com.soma.yeolo.location.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 기준 데이터셋 적재 설정 (docs/location-dataset.md).
 *
 * @param enabled        기동 시 적재 여부 (테스트 기본값 false)
 * @param countriesPath  국가 TSV 클래스패스 경로
 * @param citiesPath     도시 TSV 클래스패스 경로
 * @param cityImagesPath 도시 사진 TSV 클래스패스 경로 (없어도 기동한다 — 사진만 비게 된다)
 */
@ConfigurationProperties(prefix = "location.seed")
public record LocationSeedProperties(boolean enabled, String countriesPath, String citiesPath,
                                     String cityImagesPath) {
}
