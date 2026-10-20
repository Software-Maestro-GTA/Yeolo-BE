package com.soma.yeolo.location.dto;

import com.soma.yeolo.location.domain.LocationLanguage;
import com.soma.yeolo.location.entity.CityEntity;
import java.util.List;

/** 국가별 도시 전체 목록 응답 (API-LOC-2). 페이지네이션 없이 전부 내린다(명세). 표시명은 응답 언어를 따른다. */
public record CityListResponse(List<City> cities) {

    public record City(String cityName) {
    }

    public static CityListResponse from(List<CityEntity> entities, LocationLanguage language) {
        return new CityListResponse(entities.stream().map(city -> new City(city.displayName(language))).toList());
    }
}
