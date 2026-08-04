package com.soma.yeolo.tasteprofile.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenStreetMap(Nominatim) 기반 Reverse Geocode 설정. API 키가 필요 없다.
 *
 * <p>Nominatim 공개 서버 이용 정책상 애플리케이션을 식별하는 {@code User-Agent}(또는 연락처)
 * 헤더가 필수이며, 초당 1회 이하로 호출해야 한다. 이를 지키기 위해 클라이언트는 호출 간
 * 최소 간격({@code minIntervalMs})을 두고, 같은 좌표는 캐시({@code cacheMaxSize})로 재사용한다.
 * 자체 호스팅 서버를 쓸 경우 {@code reverseUrl}만 바꾸고 간격을 0으로 낮추면 된다.
 *
 * @param reverseUrl    Reverse Geocoding 엔드포인트 URL (Nominatim {@code /reverse})
 * @param userAgent     호출 주체를 식별하는 User-Agent 값 (Nominatim 이용 정책상 필수)
 * @param zoom          주소 상세 수준(0~18). 값이 클수록 건물/장소 단위로 상세해진다.
 * @param language      결과 언어 (예: ko) — {@code accept-language} 파라미터로 전달
 * @param minIntervalMs 연속 호출 최소 간격(ms). 공개 서버 정책(≤1req/s) 준수를 위한 기본값 1000.
 * @param cacheMaxSize  좌표→위치 결과 캐시의 최대 항목 수(LRU). 중복 좌표 재조회를 줄인다.
 */
@ConfigurationProperties(prefix = "geocode.osm")
public record OsmGeocodeProperties(
        String reverseUrl,
        String userAgent,
        Integer zoom,
        String language,
        @DefaultValue("1000") long minIntervalMs,
        @DefaultValue("4096") int cacheMaxSize
) {
}
