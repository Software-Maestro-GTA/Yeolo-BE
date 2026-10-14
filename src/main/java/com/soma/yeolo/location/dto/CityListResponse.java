package com.soma.yeolo.location.dto;

import com.soma.yeolo.location.entity.CityEntity;
import java.util.List;

/** 국가별 도시 전체 목록 응답 (API-LOC-2). 페이지네이션 없이 전부 내린다(명세). */
public record CityListResponse(List<City> cities) {

    public record City(String cityName) {
        static City from(CityEntity entity) {
            return new City(entity.getNameKo());
        }
    }

    public static CityListResponse from(List<CityEntity> entities) {
        return new CityListResponse(entities.stream().map(City::from).toList());
    }
}
