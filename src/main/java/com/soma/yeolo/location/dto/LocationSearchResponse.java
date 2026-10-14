package com.soma.yeolo.location.dto;

import java.util.List;

/**
 * 국가·도시 키워드 검색 응답 (API-LOC-3). 국가별로 묶인 도시 표시명 목록이다. 국가명 매칭으로 전체
 * 도시가 포함된 국가도, 도시명만 매칭된 국가도 같은 모양이다.
 */
public record LocationSearchResponse(List<CountryResult> results) {

    public record CountryResult(String countryName, List<City> cities) {
    }

    public record City(String cityName) {
    }
}
