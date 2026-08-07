package com.soma.yeolo.location.dto;

import com.soma.yeolo.location.entity.CountryEntity;
import java.util.List;

/**
 * 국가 자동완성 조회 응답의 {@code data} 페이로드 (API-LOC-1). 필드명·구조는 명세 그대로다.
 *
 * <p>후보가 없으면 {@code countries}는 빈 배열이다({@code null} 아님) — 인수 기준
 * "결과가 없으면 빈 배열이 반환된다".
 */
public record CountryAutocompleteResponse(List<Country> countries) {

    /**
     * 국가 후보.
     *
     * @param countryId    ISO 3166-1 alpha-2 국가 코드
     * @param countryNameKo 국가 한국어명
     */
    public record Country(String countryId, String countryNameKo) {

        static Country from(CountryEntity entity) {
            return new Country(entity.getCountryId(), entity.getNameKo());
        }
    }

    public static CountryAutocompleteResponse from(List<CountryEntity> entities) {
        return new CountryAutocompleteResponse(entities.stream().map(Country::from).toList());
    }
}
