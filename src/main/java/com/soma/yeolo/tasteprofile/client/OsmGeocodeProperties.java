package com.soma.yeolo.tasteprofile.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenStreetMap(Nominatim) 기반 Reverse Geocode 설정. API 키가 필요 없다.
 *
 * <p>호스트 단위 이용 정책(User-Agent, 호출 간격)은 이 기능만의 것이 아니므로
 * {@link com.soma.yeolo.global.client.NominatimProperties}가 소유한다. 여기에는 역지오코딩에만
 * 해당하는 설정만 둔다. 자체 호스팅 서버를 쓸 경우 {@code reverseUrl}만 바꾸면 된다.
 *
 * @param reverseUrl   Reverse Geocoding 엔드포인트 URL (Nominatim {@code /reverse})
 * @param zoom         주소 상세 수준(0~18). 값이 클수록 건물/장소 단위로 상세해진다.
 * @param language     결과 언어 (예: ko) — {@code accept-language} 파라미터로 전달
 * @param cacheMaxSize 좌표→위치 결과 캐시의 최대 항목 수(LRU). 중복 좌표 재조회를 줄인다.
 */
@ConfigurationProperties(prefix = "geocode.osm")
public record OsmGeocodeProperties(
        String reverseUrl,
        Integer zoom,
        String language,
        @DefaultValue("4096") int cacheMaxSize
) {
}
