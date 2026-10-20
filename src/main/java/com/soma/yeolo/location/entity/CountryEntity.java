package com.soma.yeolo.location.entity;

import com.soma.yeolo.location.domain.LocationLanguage;
import com.soma.yeolo.location.domain.SearchKeys;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 국가 기준 데이터 (API-LOC-1/2/3). 저장소의 {@code countries.tsv}를 기동 시 적재한 파생물이다
 * (docs/location-dataset.md).
 *
 * <p>2026-10 명세 개정으로 자동완성(부분 일치·초성)이 전체 목록·접두어 검색으로 바뀌었다.
 * {@code search_chosung}은 더 이상 조회에 쓰지 않지만, dev 의 {@code ddl-auto=update}는 컬럼을
 * 지우지 않고 NOT NULL 이라 적재 INSERT 가 깨지므로 값은 계속 채운다(정리는 docs/ddl/countries.sql).
 * 수도·통화는 같은 개정에서 추가됐다 — 수도는 추천 도시 사진 폴백(#97), 통화 기호는 코스 상세의
 * {@code exRateIcon}(#100)에 쓰인다.
 *
 * <p><b>표시명은 한국어·영어 두 벌이다</b>(언어별 표시, {@link LocationLanguage}). 영문 열은 기존 환경에
 * 행이 있는 채로 추가되므로 NOT NULL 을 걸지 않는다 — dev 의 {@code ddl-auto=update}가 NOT NULL 컬럼을
 * 더하려다 실패하면 적재까지 막힌다. 값은 적재가 항상 채우고, 비어 있으면 한국어명으로 보인다.
 */
@Getter
@Entity
@Table(name = "countries", indexes = {
        @Index(name = "idx_countries_search_chosung", columnList = "search_chosung"),
        @Index(name = "idx_countries_search_name", columnList = "search_name"),
        @Index(name = "idx_countries_search_name_en", columnList = "search_name_en")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CountryEntity {

    /** ISO 3166-1 alpha-2. */
    @Id
    @Column(name = "country_id", length = 2, nullable = false, updatable = false)
    private String countryId;

    /** 한국어 표시명(CLDR 한국어 지역명). {@code Accept-Language: ko}의 {@code countryName}. */
    @Column(name = "name_ko", nullable = false, columnDefinition = "text")
    private String nameKo;

    /** 영문 표시명(CLDR 영어 지역명). {@code Accept-Language: en}의 {@code countryName}. */
    @Column(name = "name_en", columnDefinition = "text")
    private String nameEn;

    /** 한국어명의 접두어·완전 일치 검색 키(소문자·구분자 제거). */
    @Column(name = "search_name", nullable = false, columnDefinition = "text")
    private String searchName;

    /** 영문명의 검색 키. 검색·완전 일치 조회는 두 키를 모두 본다(입력 언어와 응답 언어는 별개 — 명세). */
    @Column(name = "search_name_en", columnDefinition = "text")
    private String searchNameEn;

    /** 초성 검색 키 — 2026-10 개정으로 조회에는 쓰지 않는다(위 클래스 설명). */
    @Column(name = "search_chosung", nullable = false, columnDefinition = "text")
    private String searchChosung;

    /** 수도의 {@code cities.city_id}. 수도가 데이터셋에 없으면 null. */
    @Column(name = "capital_city_id", length = 32)
    private String capitalCityId;

    /** ISO 4217 통화 코드. 통용 통화가 없는 영토(남극 등)는 null. */
    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    /** 현지 통화 기호(CLDR 한국어 좁은 기호 우선: ¥·$·฿). 코드만 있으면 코드와 같다. */
    @Column(name = "currency_symbol", columnDefinition = "text")
    private String currencySymbol;

    public static CountryEntity of(String countryId, String nameKo, String nameEn) {
        return of(countryId, nameKo, nameEn, null, null, null);
    }

    public static CountryEntity of(String countryId, String nameKo, String nameEn, String capitalCityId,
                                   String currencyCode, String currencySymbol) {
        CountryEntity entity = new CountryEntity();
        entity.countryId = countryId;
        entity.nameKo = nameKo;
        entity.nameEn = blankToNull(nameEn);
        entity.searchName = SearchKeys.normalize(nameKo);
        entity.searchNameEn = entity.nameEn == null ? null : SearchKeys.normalize(entity.nameEn);
        entity.searchChosung = SearchKeys.chosung(nameKo);
        entity.capitalCityId = blankToNull(capitalCityId);
        entity.currencyCode = blankToNull(currencyCode);
        entity.currencySymbol = blankToNull(currencySymbol);
        return entity;
    }

    /** 응답 언어의 표시명 ({@link LocationLanguage#pick}). */
    public String displayName(LocationLanguage language) {
        return language.pick(nameKo, nameEn);
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }
}
