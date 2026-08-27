package com.soma.yeolo.location.dto;

import com.soma.yeolo.location.entity.CityEntity;
import java.util.List;

/**
 * 도시 자동완성 조회 응답의 {@code data} 페이로드 (API-LOC-2). 필드명·구조는 명세 그대로다.
 *
 * <p>도시는 동명이 많아 국가명 없이는 어느 도시인지 알 수 없으므로, 명세대로 소속 국가를 함께 준다.
 * 후보가 없으면 {@code cities}는 빈 배열이다.
 */
public record CityAutocompleteResponse(List<City> cities) {

    /**
     * 도시 후보.
     *
     * @param cityId        도시 식별자 (GeoNames geonameId)
     * @param cityNameKo    도시 한국어명 — 한국어 이름이 없는 도시는 원문(라틴) 표기가 온다
     * @param countryId     소속 국가의 ISO 3166-1 alpha-2 코드
     * @param countryNameKo 소속 국가 한국어명
     */
    public record City(String cityId, String cityNameKo, String countryId, String countryNameKo) {

        static City from(CityEntity entity) {
            return new City(entity.getCityId(), entity.getNameKo(),
                    entity.getCountryId(), entity.getCountryNameKo());
        }
    }

    public static CityAutocompleteResponse from(List<CityEntity> entities) {
        return new CityAutocompleteResponse(entities.stream().map(City::from).toList());
    }
}
