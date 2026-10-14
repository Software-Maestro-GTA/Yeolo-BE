package com.soma.yeolo.location.dto;

import com.soma.yeolo.location.entity.CountryEntity;
import java.util.List;

/**
 * 국가 전체 목록 응답 (API-LOC-1). 명세 JSON 스키마대로 표시명만 내린다 — 본문은 {@code countryId}를
 * 언급하지만 스키마에는 없고, 기존 관례대로 JSON 스키마를 계약으로 본다(docs/spec-index.md).
 */
public record CountryListResponse(List<Country> countries) {

    public record Country(String countryName) {
        static Country from(CountryEntity entity) {
            return new Country(entity.getNameKo());
        }
    }

    public static CountryListResponse from(List<CountryEntity> entities) {
        return new CountryListResponse(entities.stream().map(Country::from).toList());
    }
}
