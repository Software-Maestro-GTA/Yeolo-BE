package com.soma.yeolo.place.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenStreetMap(Nominatim) 기반 장소 조회 설정. API 키가 필요 없다.
 *
 * <p>호스트 단위 이용 정책(User-Agent, 호출 간격)은 이 기능만의 것이 아니므로
 * {@link com.soma.yeolo.global.client.NominatimProperties}가 소유한다. 여기에는 장소 조회에만
 * 해당하는 설정만 둔다.
 *
 * @param searchUrl Search 엔드포인트 URL (Nominatim {@code /search})
 * @param language  결과 언어 (예: ko) — {@code accept-language} 파라미터로 전달
 */
@ConfigurationProperties(prefix = "place.osm")
public record OsmPlaceProperties(String searchUrl, String language) {
}
