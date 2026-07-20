package com.soma.yeolo.tasteprofile.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Maps API 연동 설정. API 키는 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 *
 * @param apiKey           Google Maps API 키
 * @param geocodeUrl       Reverse Geocoding 엔드포인트 URL (행정구역 추출)
 * @param placesNearbyUrl  Places Nearby Search 엔드포인트 URL (POI 장소명·유형 추출)
 * @param nearbyRadius     Nearby Search 반경(m). 좌표 주변에서 대표 장소를 찾을 범위.
 * @param language         결과 언어 (예: ko)
 */
@ConfigurationProperties(prefix = "geocode.google")
public record GoogleGeocodeProperties(
        String apiKey,
        String geocodeUrl,
        String placesNearbyUrl,
        Integer nearbyRadius,
        String language
) {
}
