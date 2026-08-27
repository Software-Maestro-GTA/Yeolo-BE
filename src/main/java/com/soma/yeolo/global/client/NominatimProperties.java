package com.soma.yeolo.global.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * OpenStreetMap(Nominatim) 공개 서버의 <b>호스트 단위 이용 정책</b> 설정.
 *
 * <p>역지오코딩(DOM-5)과 장소 조회(DOM-3)가 같은 호스트를 호출하고, Nominatim의 User-Agent 요구와
 * "초당 1회 이하" 제한은 <b>기능이 아니라 호출자(IP) 단위</b>로 적용된다. 그래서 이 두 값은 기능별
 * 설정({@code geocode.osm.*}, {@code place.osm.*})이 아니라 여기 한 곳에서만 정의한다 — 기능마다
 * 따로 두면 값이 갈리고 합산 호출량이 정책을 넘긴다.
 *
 * <p>자체 호스팅 서버를 쓴다면 {@code minIntervalMs}를 0으로 낮추면 된다.
 *
 * @param userAgent     호출 주체를 식별하는 User-Agent 값 (이용 정책상 필수)
 * @param minIntervalMs 연속 호출 최소 간격(ms). 공개 서버 정책(≤1req/s) 준수를 위한 기본값 1000.
 */
@ConfigurationProperties(prefix = "nominatim")
public record NominatimProperties(
        String userAgent,
        @DefaultValue("1000") long minIntervalMs
) {
}
