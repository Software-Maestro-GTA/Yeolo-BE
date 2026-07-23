package com.soma.yeolo.tasteprofile.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenStreetMap(Nominatim) 기반 Reverse Geocode 설정. API 키가 필요 없다.
 *
 * <p>Nominatim 공개 서버 이용 정책상 애플리케이션을 식별하는 {@code User-Agent}(또는 연락처)
 * 헤더가 필수이며, 초당 1회 이하로 호출해야 한다. 자체 호스팅 서버를 쓸 경우 {@code reverseUrl}만
 * 바꿔주면 된다.
 *
 * @param reverseUrl Reverse Geocoding 엔드포인트 URL (Nominatim {@code /reverse})
 * @param userAgent  호출 주체를 식별하는 User-Agent 값 (Nominatim 이용 정책상 필수)
 * @param zoom       주소 상세 수준(0~18). 값이 클수록 건물/장소 단위로 상세해진다.
 * @param language   결과 언어 (예: ko) — {@code accept-language} 파라미터로 전달
 */
@ConfigurationProperties(prefix = "geocode.osm")
public record OsmGeocodeProperties(
        String reverseUrl,
        String userAgent,
        Integer zoom,
        String language
) {
}
