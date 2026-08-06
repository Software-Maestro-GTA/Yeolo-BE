package com.soma.yeolo.place.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Places 기반 장소 조회 설정. API 키는 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 *
 * @param apiKey        Google Maps API 키
 * @param textSearchUrl Places Text Search 엔드포인트 URL (장소명 → 후보 장소)
 * @param detailsUrl    Place Details 엔드포인트 URL (운영시간 등 상세 보강)
 * @param language      결과 언어 (예: ko)
 */
@ConfigurationProperties(prefix = "place.google")
public record GooglePlaceProperties(
        String apiKey,
        String textSearchUrl,
        String detailsUrl,
        String language
) {
}
