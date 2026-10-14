package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
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

/** API-LOC-1/2/3 (2026-10 개정) — 전체 목록·국가별 도시·접두어 검색의 규칙을 고정한다. */
@ExtendWith(MockitoExtension.class)
class LocationQueryServiceTest {

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private CityRepository cityRepository;

    @InjectMocks
    private LocationQueryService service;

    private static final CountryEntity JAPAN = CountryEntity.of("JP", "일본");
    private static final CountryEntity KOREA = CountryEntity.of("KR", "대한민국");
    private static final CountryEntity DENMARK = CountryEntity.of("DK", "덴마크");

    private CityEntity city(String id, String nameKo, CountryEntity country) {
        return CityEntity.of(id, nameKo, country.getCountryId(), country.getNameKo(), 0L);
    }

    // ===== API-LOC-1 국가 전체 목록 =====

    @Test
    void 국가_목록은_표시명만_담아_그대로_돌려준다() {
        when(countryRepository.findAllByOrderByNameKoAsc()).thenReturn(List.of(KOREA, DENMARK, JAPAN));

        CountryListResponse response = service.listCountries();

        assertThat(response.countries()).extracting(CountryListResponse.Country::countryName)
                .containsExactly("대한민국", "덴마크", "일본");
    }

    @Test
    void 국가가_없으면_빈_배열이다() {
        when(countryRepository.findAllByOrderByNameKoAsc()).thenReturn(List.of());

        assertThat(service.listCountries().countries()).isEmpty();
    }

    // ===== API-LOC-2 국가별 도시 전체 목록 =====

    @Test
    void 국가명으로_그_국가의_도시_전체를_돌려준다() {
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findByCountryIdOrderByNameKoAsc("JP"))
                .thenReturn(List.of(city("1", "교토", JAPAN), city("2", "도쿄", JAPAN), city("3", "오사카", JAPAN)));

        CityListResponse response = service.listCities("일본");

        assertThat(response.cities()).extracting(CityListResponse.City::cityName)
                .containsExactly("교토", "도쿄", "오사카");
    }

    /** 명세: 앞뒤 공백 제거 후 완전 일치, 영문 대소문자 구분 없음. 검색 키 정규화가 이를 맡는다. */
    @Test
    void 국가명은_공백과_대소문자를_무시하고_완전_일치로_찾는다() {
        when(countryRepository.findBySearchName("newzealand"))
                .thenReturn(Optional.of(CountryEntity.of("NZ", "New Zealand")));
        when(cityRepository.findByCountryIdOrderByNameKoAsc("NZ")).thenReturn(List.of());

        assertThat(service.listCities("  new ZEALAND ").cities()).isEmpty();
    }

    @Test
    void 없는_국가면_404다() {
        when(countryRepository.findBySearchName("없는나라")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listCities("없는나라"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.COUNTRY_NOT_FOUND);
        verifyNoInteractions(cityRepository);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "-.-"})
    void 국가명이_비면_400이다(String countryName) {
        assertThatThrownBy(() -> service.listCities(countryName))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COUNTRY_NAME);
        verifyNoInteractions(countryRepository, cityRepository);
    }

    // ===== API-LOC-3 키워드 검색 =====

    /** 매칭 규칙 1: 국가명 완전 일치 → 그 국가의 전체 도시. 접두어 검색은 하지 않는다. */
    @Test
    void 국가명과_완전_일치하면_그_국가의_전체_도시를_돌려준다() {
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findByCountryIdOrderByNameKoAsc("JP"))
                .thenReturn(List.of(city("1", "도쿄", JAPAN), city("2", "오사카", JAPAN)));

        LocationSearchResponse response = service.search("일본");

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).countryName()).isEqualTo("일본");
        assertThat(response.results().get(0).cities()).extracting(City::cityName)
                .containsExactly("도쿄", "오사카");
        verify(countryRepository, never()).searchByPrefix(anyString());
        verify(cityRepository, never()).searchByPrefix(anyString());
    }

    /**
     * 매칭 규칙 2·3: 국가명 접두어 → 전체 도시, 도시명 접두어 → 소속 국가 아래 매칭 도시만. 국가 순서와
     * 도시 순서는 DB 가 준 순서를 그대로 쓴다(자바 재정렬 없음 — API 간 정렬 기준을 하나로).
     */
    @Test
    void 접두어로_국가와_도시를_함께_찾고_국가별로_묶는다() {
        when(countryRepository.findBySearchName("ㄷ")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("ㄷ%")).thenReturn(List.of(KOREA, DENMARK));
        when(cityRepository.searchByPrefix("ㄷ%")).thenReturn(List.of(
                city("1", "대구", KOREA),            // 국가명 매칭으로 이미 전체가 들어간 국가 → 다시 더하지 않는다
                city("11", "다카마쓰", JAPAN),
                city("10", "도쿄", JAPAN),
                city("11", "다카마쓰", JAPAN)));      // 중복 행 → 식별자 기준 제거
        // 전체 도시는 국가명 매칭 국가(KR·DK)만 한 번에 묻는다.
        when(cityRepository.findByCountryIdInOrderByCountryIdAscNameKoAsc(Set.of("KR", "DK")))
                .thenReturn(List.of(city("1", "대구", KOREA), city("2", "서울", KOREA)));
        // 국가 순서는 DB 정렬(표시명 오름차순)을 따른다.
        when(countryRepository.findByCountryIdInOrderByNameKoAsc(Set.of("KR", "DK", "JP")))
                .thenReturn(List.of(KOREA, DENMARK, JAPAN));

        LocationSearchResponse response = service.search("ㄷ");

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
        verify(cityRepository, never()).findByCountryIdOrderByNameKoAsc(anyString());
    }

    /** 도시명만 매칭되면 국가 전체 목록을 묻지 않는다 — 전체 도시 조회는 국가명 매칭 집합이 비면 생략한다. */
    @Test
    void 도시명만_매칭되면_전체_도시_일괄_조회를_생략한다() {
        when(countryRepository.findBySearchName("도쿄")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("도쿄%")).thenReturn(List.of());
        when(cityRepository.searchByPrefix("도쿄%")).thenReturn(List.of(city("10", "도쿄", JAPAN)));
        when(countryRepository.findByCountryIdInOrderByNameKoAsc(Set.of("JP"))).thenReturn(List.of(JAPAN));

        LocationSearchResponse response = service.search("도쿄");

        assertThat(response.results()).hasSize(1);
        assertThat(response.results().get(0).cities()).extracting(City::cityName).containsExactly("도쿄");
        verify(cityRepository, never()).findByCountryIdInOrderByCountryIdAscNameKoAsc(any(Collection.class));
    }

    @Test
    void 검색은_영문_대소문자와_공백을_구분하지_않는다() {
        when(countryRepository.findBySearchName("newy")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("newy%")).thenReturn(List.of());
        when(cityRepository.searchByPrefix("newy%")).thenReturn(List.of());

        service.search("  New Y ");

        verify(cityRepository).searchByPrefix("newy%");
    }

    @Test
    void 결과가_없으면_빈_배열이다() {
        when(countryRepository.findBySearchName("없는곳")).thenReturn(Optional.empty());
        when(countryRepository.searchByPrefix("없는곳%")).thenReturn(List.of());
        when(cityRepository.searchByPrefix("없는곳%")).thenReturn(List.of());

        assertThat(service.search("없는곳").results()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "-.-"})
    void 키워드가_비면_명세의_400이다(String keyword) {
        assertThatThrownBy(() -> service.search(keyword))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SEARCH_KEYWORD);
        verifyNoInteractions(countryRepository, cityRepository);
    }

    @Test
    void 지나치게_긴_키워드는_조회하지_않고_400이다() {
        assertThatThrownBy(() -> service.search("가".repeat(101)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_SEARCH_KEYWORD);
        verifyNoInteractions(countryRepository, cityRepository);
    }
}
