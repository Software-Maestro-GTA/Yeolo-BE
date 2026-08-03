package com.soma.yeolo.course.client;

import lombok.extern.slf4j.Slf4j;

/**
 * 장소 정규화 스텁 구현(기본값). 실제 지오코딩 없이 장소명 기준 내부 {@code placeId}만 결정적으로
 * 부여하고 좌표는 비운다. API 키·외부 호출 없이 코스 생성·저장 흐름을 검증할 수 있게 한다.
 *
 * <p>{@code geocode.provider=osm}으로 설정하면 {@link OsmPlaceNormalizer}가 대신 활성화된다
 * ({@code PlaceNormalizerConfig}에서 provider별로 주입).
 */
@Slf4j
public class StubPlaceNormalizer implements PlaceNormalizer {

    @Override
    public NormalizedPlace normalize(String placeName, String category, String city, String country) {
        log.debug("Place normalize (stub) for '{}' ({}, {})", placeName, city, country);
        return NormalizedPlace.of(country, city, placeName, null, null);
    }
}
