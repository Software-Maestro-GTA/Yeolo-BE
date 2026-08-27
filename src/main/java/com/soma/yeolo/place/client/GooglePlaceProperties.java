package com.soma.yeolo.place.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Places API (New) 기반 장소 조회 설정. API 키는 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 *
 * <p>구 Places API({@code maps.googleapis.com/maps/api/place/*})는 2025-03 이후 생성된 프로젝트에서
 * 활성화 자체가 불가능해(호출 시 {@code REQUEST_DENIED} + "legacy API ... not enabled"),
 * {@code places.googleapis.com}의 신 API를 쓴다. 신 API의 Text Search는 운영시간까지 한 번에 주므로
 * 별도의 Details 호출(구 {@code details-url})이 필요 없다.
 *
 * @param apiKey        Google Maps API 키
 * @param searchTextUrl Places API (New) Text Search 엔드포인트 URL (장소명 → 후보 장소)
 * @param language      결과 언어 (예: ko)
 */
@ConfigurationProperties(prefix = "place.google")
public record GooglePlaceProperties(
        String apiKey,
        String searchTextUrl,
        String language
) {
}
