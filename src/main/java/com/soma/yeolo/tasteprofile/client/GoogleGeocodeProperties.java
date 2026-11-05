package com.soma.yeolo.tasteprofile.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Google Maps API 연동 설정. API 키는 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 *
 * <p>Geocoding은 기존 {@code maps.googleapis.com} API를 그대로 쓰지만, 주변 장소 조회는 구 Places API가
 * 신규 프로젝트에서 활성화되지 않아 {@code places.googleapis.com}의 Nearby Search (New)를 쓴다.
 *
 * @param apiKey          Google Maps API 키
 * @param geocodeUrl      Reverse Geocoding 엔드포인트 URL (행정구역 추출)
 * @param searchNearbyUrl Places API (New) Nearby Search 엔드포인트 URL (POI 장소명·유형 추출)
 * @param nearbyRadius    Nearby Search 반경(m). 사진 위치 주변에서 대표 장소를 찾을 범위(1~50,000).
 *                        Nearby가 꺼져 있으면(URL 비움) 읽지 않는다.
 * @param language        결과 언어 (예: ko)
 * @param cacheGridDecimals 캐시 격자의 좌표 소수 자릿수(3~7). 3이면 약 100m 칸이고, 같은 칸의 사진은 먼저 온
 *                          사진의 좌표로 한 번만 조회한다. 키우면 칸이 작아져 적중률이 떨어지고, 줄이면 멀리
 *                          떨어진 사진끼리 한 답을 공유한다.
 * @param cacheMaxSize    격자 칸→위치 결과 캐시의 최대 항목 수(LRU, 1 이상).
 */
@ConfigurationProperties(prefix = "geocode.google")
public record GoogleGeocodeProperties(
        String apiKey,
        String geocodeUrl,
        String searchNearbyUrl,
        @DefaultValue("100") Integer nearbyRadius,
        String language,
        @DefaultValue("3") int cacheGridDecimals,
        @DefaultValue("4096") int cacheMaxSize
) {
}
