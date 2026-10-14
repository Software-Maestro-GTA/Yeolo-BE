package com.soma.yeolo.location.service;

import java.util.Optional;

/**
 * 국가명·도시명으로 대표 사진 URL 을 찾는 포트. 취향 요약의 추천 도시(API-PREF-4
 * {@code recommendCity.imageUrl}, #98)와 코스 목록의 대표 이미지 폴백(API-COURSE-3)이 쓴다.
 *
 * <p>폴백 순서는 도시 사진(완전 일치, 없으면 접두어 일치 — "부산"→"부산광역시") → 그 나라 수도 사진 →
 * 그 나라에서 사진이 있는 최다 인구 도시 → 빈 값이다.
 * 사진이 없다고 호출자의 응답을 실패시키지 않는다.
 */
public interface LocationImageReader {

    /**
     * @param countryName 표시명(예: "일본"). 적재된 국가명과 정규화 후 완전 일치해야 한다
     * @param cityName    표시명(예: "도쿄"). 없거나 못 찾으면 수도·대표 도시로 폴백한다
     * @return 공개 URL. 국가를 못 찾거나 사진이 하나도 없거나 base-url 미설정이면 빈 값
     */
    Optional<String> findCityImageUrl(String countryName, String cityName);
}
