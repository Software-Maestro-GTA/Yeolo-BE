package com.soma.yeolo.place.domain;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 장소 정규화 질의 (DOM-3 §"장소 정보 처리 기준"). AI가 반환한 장소명·분류와 코스의 목적지를 묶어
 * 외부 provider 조회 입력으로 쓴다.
 *
 * <p>목적지(국가·도시)를 함께 넘기는 이유는 동명 장소를 구분하기 위해서다 — "스타벅스"처럼 이름만으로는
 * 전 세계에 수천 곳이 잡히므로, 코스의 목적지로 검색 범위를 좁힌다.
 *
 * @param placeName 장소명 (필수)
 * @param category  AI가 붙인 장소 분류 (없으면 null)
 * @param country   여행 국가
 * @param city      여행 도시
 */
public record PlaceQuery(String placeName, String category, String country, String city) {

    public PlaceQuery {
        if (placeName == null || placeName.isBlank()) {
            throw new IllegalArgumentException("placeName is required");
        }
    }

    /** provider 검색어: "장소명, 도시, 국가" 형태로 조합한다(빈 값은 건너뛴다). */
    public String searchText() {
        return Stream.of(placeName, city, country)
                .filter(value -> value != null && !value.isBlank())
                .map(String::strip)
                .collect(Collectors.joining(", "));
    }
}
