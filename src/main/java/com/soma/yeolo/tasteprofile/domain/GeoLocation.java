package com.soma.yeolo.tasteprofile.domain;

import java.util.List;

/**
 * Reverse Geocode로 추출한 위치 정보 (DOM-5 §4-3). AI 서버로 전달하는 {@code location} 블록에 대응.
 *
 * @param country    촬영 국가
 * @param city       촬영 도시
 * @param region     촬영 지역 또는 광역 행정구역
 * @param district   구/군/동 등 세부 행정구역
 * @param placeName  좌표와 가장 가까운 장소명
 * @param placeTypes 장소 유형 목록 (tourist_attraction, cafe, beach 등)
 */
public record GeoLocation(
        String country,
        String city,
        String region,
        String district,
        String placeName,
        List<String> placeTypes
) {

    public GeoLocation {
        placeTypes = placeTypes == null ? List.of() : List.copyOf(placeTypes);
    }
}
