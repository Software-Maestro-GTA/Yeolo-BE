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
 * @param nearbyRadius    Nearby Search 반경(m). 좌표 주변에서 대표 장소를 찾을 범위.
 * @param language        결과 언어 (예: ko)
 */
@ConfigurationProperties(prefix = "geocode.google")
public record GoogleGeocodeProperties(
        String apiKey,
        String geocodeUrl,
        String searchNearbyUrl,
        @DefaultValue("100") Integer nearbyRadius,
        String language
) {
}
