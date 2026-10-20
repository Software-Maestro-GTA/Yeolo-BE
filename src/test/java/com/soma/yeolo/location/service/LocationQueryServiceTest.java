package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.location.domain.LocationLanguage;
import com.soma.yeolo.location.dto.CityListResponse;
import com.soma.yeolo.location.dto.CountryListResponse;
import com.soma.yeolo.location.dto.LocationSearchResponse;
import com.soma.yeolo.location.dto.LocationSearchResponse.City;
import com.soma.yeolo.location.dto.LocationSearchResponse.CountryResult;
import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

/**
 * API-LOC-1/2/3 (2026-10 개정) — 전체 목록·국가별 도시·접두어 검색의 규칙과 응답 언어(표시명·정렬)를
 * 고정한다. 정렬은 DB 에 맡기므로 여기서는 "응답 언어의 표시명 속성으로 정렬을 요청하는가"를 본다.
 */
@ExtendWith(MockitoExtension.class)
class LocationQueryServiceTest {

    private static final LocationLanguage KO = LocationLanguage.KO;
    private static final LocationLanguage EN = LocationLanguage.EN;
    private static final Sort BY_KO = Sort.by("nameKo");
    private static final Sort BY_EN = Sort.by("nameEn");
    private static final Sort BY_COUNTRY_KO = Sort.by("countryId").and(BY_KO);

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private CityRepository cityRepository;

    @InjectMocks
    private LocationQueryService service;

    private static final CountryEntity JAPAN = CountryEntity.of("JP", "일본", "Japan");
    private static final CountryEntity KOREA = CountryEntity.of("KR", "대한민국", "South Korea");
    private static final CountryEntity DENMARK = CountryEntity.of("DK", "덴마크", "Denmark");

    private CityEntity city(String id, String nameKo, String nameEn, CountryEntity country) {
        return CityEntity.of(id, nameKo, nameEn, country.getCountryId(), country.getNameKo(), 0L);
    }

    // ===== API-LOC-1 국가 전체 목록 =====

    @Test
    void 국가_목록은_한국어_표시명만_담아_그대로_돌려준다() {
        when(countryRepository.findAll(BY_KO)).thenReturn(List.of(KOREA, DENMARK, JAPAN));

        CountryListResponse response = service.listCountries(KO);

        assertThat(response.countries()).extracting(CountryListResponse.Country::countryName)
                .containsExactly("대한민국", "덴마크", "일본");
    }

    /** Accept-Language: en — 영문 표시명으로 정렬을 요청하고 영문 표시명을 내린다. */
    @Test
    void 영어_요청이면_영문_표시명과_영문_정렬이다() {
        when(countryRepository.findAll(BY_EN)).thenReturn(List.of(DENMARK, JAPAN, KOREA));

        CountryListResponse response = service.listCountries(EN);

        assertThat(response.countries()).extracting(CountryListResponse.Country::countryName)
                .containsExactly("Denmark", "Japan", "South Korea");
        verify(countryRepository, never()).findAll(BY_KO);
    }

    @Test
    void 국가가_없으면_빈_배열이다() {
        when(countryRepository.findAll(BY_KO)).thenReturn(List.of());

        assertThat(service.listCountries(KO).countries()).isEmpty();
    }

    // ===== API-LOC-2 국가별 도시 전체 목록 =====

    @Test
    void 국가명으로_그_국가의_도시_전체를_돌려준다() {
        when(countryRepository.findByAnySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findByCountryId("JP", BY_KO)).thenReturn(List.of(
                city("1", "교토", "Kyoto", JAPAN), city("2", "도쿄", "Tokyo", JAPAN), city("3", "오사카", "Osaka", JAPAN)));

        CityListResponse response = service.listCities("일본", KO);

        assertThat(response.cities()).extracting(CityListResponse.City::cityName)
                .containsExactly("교토", "도쿄", "오사카");
    }

    /** 명세: 검색 입력 언어와 응답 언어는 별개 — 영문 국가명으로 물어도 한국어 응답, 한국어로 물어도 영문 응답. */
    @Test
    void 국가명_언어와_응답_언어는_별개다() {
        when(countryRepository.findByAnySearchName("japan")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findByCountryId("JP", BY_KO)).thenReturn(List.of(city("2", "도쿄", "Tokyo", JAPAN)));
        when(countryRepository.findByAnySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findByCountryId("JP", BY_EN)).thenReturn(List.of(city("2", "도쿄", "Tokyo", JAPAN)));

        assertThat(service.listCities("Japan", KO).cities()).extracting(CityListResponse.City::cityName)
                .containsExactly("도쿄");
        assertThat(service.listCities("일본", EN).cities()).extracting(CityListResponse.City::cityName)
                .containsExactly("Tokyo");
    }

    /** 명세: 앞뒤 공백 제거 후 완전 일치, 영문 대소문자 구분 없음. 검색 키 정규화가 이를 맡는다. */
    @Test
    void 국가명은_공백과_대소문자를_무시하고_완전_일치로_찾는다() {
        when(countryRepository.findByAnySearchName("newzealand"))
                .thenReturn(Optional.of(CountryEntity.of("NZ", "뉴질랜드", "New Zealand")));
        when(cityRepository.findByCountryId("NZ", BY_KO)).thenReturn(List.of());

        assertThat(service.listCities("  new ZEALAND ", KO).cities()).isEmpty();
    }

    @Test
    void 없는_국가면_404다() {
        when(countryRepository.findByAnySearchName("없는나라")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listCities("없는나라", KO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COUNTRY_NOT_FOUND);
        verifyNoInteractions(cityRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "-.-"})
    void 국가명이_비면_400이다(String countryName) {
        assertThatThrownBy(() -> service.listCities(countryName, KO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COUNTRY_NAME);
        verifyNoInteractions(countryRepository, cityRepository);
    }

    // ===== API-LOC-3 키워드 검색 =====

    /** 매칭 규칙 1: 국가명 완전 일치 → 그 국가의 전체 도시. 접두어 검색은 하지 않는다. */
    @Test
    void 국가명과_완전_일치하면_그_국가의_전체_도시를_돌려준다() {
        when(countryRepository.findByAnySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findByCountryId("JP", BY_KO))
                .thenReturn(List.of(city("1", "도쿄", "Tokyo", JAPAN), city("2", "오사카", "Osaka", JAPAN)));

        LocationSearchResponse response = service.search("일본", KO);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).countryName()).isEqualTo("일본");
        assertThat(response.results().get(0).cities()).extracting(City::cityName)
                .containsExactly("도쿄", "오사카");
        verify(countryRepository, never()).searchByPrefix(anyString(), any(Sort.class));
        verify(cityRepository, never()).searchByPrefix(anyString(), any(Sort.class));
    }

    /**
     * 매칭 규칙 2·3: 국가명 접두어 → 전체 도시, 도시명 접두어 → 소속 국가 아래 매칭 도시만. 국가 순서와
     * 도시 순서는 DB 가 준 순서를 그대로 쓴다(자바 재정렬 없음 — API 간 정렬 기준을 하나로).
     */
    @Test
    void 접두어로_국가와_도시를_함께_찾고_국가별로_묶는다() {
        when(countryRepository.findByAnySearchName("ㄷ")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("ㄷ%", BY_KO)).thenReturn(List.of(KOREA, DENMARK));
        when(cityRepository.searchByPrefix("ㄷ%", BY_COUNTRY_KO)).thenReturn(List.of(
                city("1", "대구", "Daegu", KOREA),            // 국가명 매칭으로 이미 전체가 들어간 국가 → 다시 더하지 않는다
                city("11", "다카마쓰", "Takamatsu", JAPAN),
                city("10", "도쿄", "Tokyo", JAPAN),
                city("11", "다카마쓰", "Takamatsu", JAPAN)));      // 중복 행 → 식별자 기준 제거
        // 전체 도시는 국가명 매칭 국가(KR·DK)만 한 번에 묻는다.
        when(cityRepository.findByCountryIdIn(Set.of("KR", "DK"), BY_COUNTRY_KO))
                .thenReturn(List.of(city("1", "대구", "Daegu", KOREA), city("2", "서울", "Seoul", KOREA)));
        // 국가 순서는 DB 정렬(표시명 오름차순)을 따른다.
        when(countryRepository.findByCountryIdIn(Set.of("KR", "DK", "JP"), BY_KO))
                .thenReturn(List.of(KOREA, DENMARK, JAPAN));

        LocationSearchResponse response = service.search("ㄷ", KO);

        assertThat(response.results()).extracting(CountryResult::countryName)
                .containsExactly("대한민국", "덴마크", "일본");
        assertThat(response.results().get(0).cities()).extracting(City::cityName)
                .containsExactly("대구", "서울");
        // 국가가 매칭됐지만 등록 도시가 없으면 빈 배열로 돌려준다(명세).
        assertThat(response.results().get(1).cities()).isEmpty();
        // 도시명만 매칭된 국가는 DB 가 준 순서(국가별·표시명순)대로, 중복 없이.
        assertThat(response.results().get(2).cities()).extracting(City::cityName)
                .containsExactly("다카마쓰", "도쿄");
        // 국가별로 도시를 따로 묻지 않는다.
        verify(cityRepository, never()).findByCountryId(anyString(), any(Sort.class));
    }

    /** 영어 요청은 모든 정렬을 영문 표시명으로 요청하고, 국가·도시 모두 영문 표시명으로 묶는다. */
    @Test
    void 영어_요청의_검색은_영문_표시명으로_정렬하고_응답한다() {
        Sort byCountryEn = Sort.by("countryId").and(BY_EN);
        when(countryRepository.findByAnySearchName("t")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("t%", BY_EN)).thenReturn(List.of());
        when(cityRepository.searchByPrefix("t%", byCountryEn))
                .thenReturn(List.of(city("11", "다카마쓰", "Takamatsu", JAPAN), city("10", "도쿄", "Tokyo", JAPAN)));
        when(countryRepository.findByCountryIdIn(Set.of("JP"), BY_EN)).thenReturn(List.of(JAPAN));

        LocationSearchResponse response = service.search("t", EN);

        assertThat(response.results()).extracting(CountryResult::countryName).containsExactly("Japan");
        assertThat(response.results().get(0).cities()).extracting(City::cityName)
                .containsExactly("Takamatsu", "Tokyo");
        verify(cityRepository, never()).findByCountryIdIn(any(Collection.class), eq(BY_COUNTRY_KO));
    }

    /** 도시명만 매칭되면 국가 전체 목록을 묻지 않는다 — 전체 도시 조회는 국가명 매칭 집합이 비면 생략한다. */
    @Test
    void 도시명만_매칭되면_전체_도시_일괄_조회를_생략한다() {
        when(countryRepository.findByAnySearchName("도쿄")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("도쿄%", BY_KO)).thenReturn(List.of());
        when(cityRepository.searchByPrefix("도쿄%", BY_COUNTRY_KO)).thenReturn(List.of(city("10", "도쿄", "Tokyo", JAPAN)));
        when(countryRepository.findByCountryIdIn(Set.of("JP"), BY_KO)).thenReturn(List.of(JAPAN));

        LocationSearchResponse response = service.search("도쿄", KO);

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).cities()).extracting(City::cityName).containsExactly("도쿄");
        verify(cityRepository, never()).findByCountryIdIn(any(Collection.class), any(Sort.class));
    }

    @Test
    void 검색은_영문_대소문자와_공백을_구분하지_않는다() {
        when(countryRepository.findByAnySearchName("newy")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("newy%", BY_KO)).thenReturn(List.of());
        when(cityRepository.searchByPrefix("newy%", BY_COUNTRY_KO)).thenReturn(List.of());

        service.search("  New Y ", KO);

        verify(cityRepository).searchByPrefix("newy%", BY_COUNTRY_KO);
    }

    @Test
    void 결과가_없으면_빈_배열이다() {
        when(countryRepository.findByAnySearchName("없는곳")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("없는곳%", BY_KO)).thenReturn(List.of());
        when(cityRepository.searchByPrefix("없는곳%", BY_COUNTRY_KO)).thenReturn(List.of());

        assertThat(service.search("없는곳", KO).results()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "-.-"})
    void 키워드가_비면_명세의_400이다(String keyword) {
        assertThatThrownBy(() -> service.search(keyword, KO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SEARCH_KEYWORD);
        verifyNoInteractions(countryRepository, cityRepository);
    }

    @Test
    void 지나치게_긴_키워드는_조회하지_않고_400이다() {
        assertThatThrownBy(() -> service.search("가".repeat(101), KO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SEARCH_KEYWORD);
        verifyNoInteractions(countryRepository, cityRepository);
    }
}
