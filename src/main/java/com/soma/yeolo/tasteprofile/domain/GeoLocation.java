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

    /**
     * AI 서버가 분석할 수 있는 위치인지 — {@code placeTypes}를 뺀 모든 필드가 채워졌는지 판정한다.
     *
     * <p>AI의 {@code LocationSchema}(API-AI-1)는 {@code placeTypes}만 optional이고 나머지는 전부
     * non-nullable이다. 한 항목이라도 {@code null}이면 요청 <b>전체</b>가 Pydantic 검증에서 400
     * ("분석 가능한 전처리 메타데이터가 부족합니다.")으로 반려되므로, 좌표에 결과가 없는 사진 한 장이
     * 나머지 수십 장의 분석까지 죽인다. 그래서 전처리 단계에서 이 판정으로 걸러 낸다.
     */
    public boolean isAnalyzable() {
        return isPresent(country) && isPresent(city) && isPresent(region)
                && isPresent(district) && isPresent(placeName);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
