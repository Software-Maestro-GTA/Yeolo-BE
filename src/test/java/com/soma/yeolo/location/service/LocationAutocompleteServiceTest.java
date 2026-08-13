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
import com.soma.yeolo.location.dto.CityAutocompleteResponse;
import com.soma.yeolo.location.dto.CountryAutocompleteResponse;
import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/**
 * 자동완성 조회 규칙(API-LOC-1 / API-LOC-2)을 검증한다.
 *
 * <p>확인하는 것은 "어떤 쿼리로 어떤 패턴을 들고 가는가"다 — 초성 입력이 초성 컬럼으로 가는지,
 * 검색어가 LIKE 패턴으로 안전하게 감싸지는지, limit이 어떻게 정해지는지. 실제 매칭 결과는
 * {@code LocationRepositoryTest}가 DB에서 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class LocationAutocompleteServiceTest {

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private CityRepository cityRepository;

    @InjectMocks
    private LocationAutocompleteService service;

    private CountryEntity country(String id, String nameKo) {
        return CountryEntity.of(id, nameKo);
    }

    private CityEntity city(String id, String nameKo, String countryId, String countryNameKo) {
        return CityEntity.of(id, nameKo, countryId, countryNameKo, 0L);
    }

    @Test
    void 국가_검색어는_이름_컬럼을_부분_일치로_조회한다() {
        when(countryRepository.searchByName(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(country("KR", "대한민국")));

        CountryAutocompleteResponse response = service.searchCountries("대한", null);

        verify(countryRepository).searchByName("%대한%", "대한%", Pageable.ofSize(10));
        verify(countryRepository, never()).searchByChosung(anyString(), any(Pageable.class));
        assertThat(response.countries())
                .containsExactly(new CountryAutocompleteResponse.Country("KR", "대한민국"));
    }

    @Test
    void 초성만_입력하면_초성_컬럼을_앞부분_일치로_조회한다() {
        when(countryRepository.searchByChosung(anyString(), any(Pageable.class)))
                .thenReturn(List.of(country("KR", "대한민국"), country("DK", "덴마크")));

        CountryAutocompleteResponse response = service.searchCountries("ㄷ", null);

        verify(countryRepository).searchByChosung("ㄷ%", Pageable.ofSize(10));
        verify(countryRepository, never())
                .searchByName(anyString(), anyString(), any(Pageable.class));
        assertThat(response.countries()).extracting(CountryAutocompleteResponse.Country::countryId)
                .containsExactly("KR", "DK");
    }

    @Test
    void 도시_검색은_전체_국가를_대상으로_하고_국가명을_함께_준다() {
        when(cityRepository.searchByName(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of(city("1835848", "서울특별시", "KR", "대한민국")));

        CityAutocompleteResponse response = service.searchCities(null, "서울", null);

        verify(cityRepository).searchByName("%서울%", "서울%", Pageable.ofSize(10));
        assertThat(response.cities()).containsExactly(
                new CityAutocompleteResponse.City("1835848", "서울특별시", "KR", "대한민국"));
    }

    @Test
    void 도시도_초성_검색을_지원한다() {
        when(cityRepository.searchByChosung(anyString(), any(Pageable.class)))
                .thenReturn(List.of(city("1835848", "서울특별시", "KR", "대한민국")));

        service.searchCities(null, "ㅅㅇ", null);

        verify(cityRepository).searchByChosung("ㅅㅇ%", Pageable.ofSize(10));
    }

    // ===== country 필터 (API-LOC-2 명세 개정) =====

    @Test
    void 국가_코드를_주면_그_국가로_좁혀_조회한다() {
        when(cityRepository.searchByNameInCountry(anyString(), anyString(), anyString(), anyString(),
                any(Pageable.class))).thenReturn(List.of(city("1835848", "서울특별시", "KR", "대한민국")));

        CityAutocompleteResponse response = service.searchCities("KR", "서울", null);

        verify(cityRepository)
                .searchByNameInCountry("%서울%", "서울%", "KR", "KR", Pageable.ofSize(10));
        verify(cityRepository, never())
                .searchByName(anyString(), anyString(), any(Pageable.class));
        assertThat(response.cities()).hasSize(1);
    }

    /** FE가 화면에 보이는 국가명을 그대로 보낼 수 있어 둘 다 받는다. */
    @Test
    void 국가_한국어명으로도_좁힐_수_있다() {
        when(cityRepository.searchByNameInCountry(anyString(), anyString(), anyString(), anyString(),
                any(Pageable.class))).thenReturn(List.of());

        service.searchCities("대한민국", "서울", null);

        verify(cityRepository)
                .searchByNameInCountry("%서울%", "서울%", "대한민국", "대한민국", Pageable.ofSize(10));
    }

    /** 소문자 alpha-2 로 와도 국가 코드로 맞춰 준다(적재는 대문자). */
    @Test
    void 소문자_국가_코드도_대문자로_맞춘다() {
        when(cityRepository.searchByChosungInCountry(anyString(), anyString(), anyString(),
                any(Pageable.class))).thenReturn(List.of());

        service.searchCities("kr", "ㅅㅇ", null);

        verify(cityRepository).searchByChosungInCountry("ㅅㅇ%", "KR", "kr", Pageable.ofSize(10));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void 국가를_주지_않으면_전체_국가를_대상으로_조회한다(String country) {
        when(cityRepository.searchByName(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        service.searchCities(country, "서울", null);

        verify(cityRepository).searchByName("%서울%", "서울%", Pageable.ofSize(10));
    }

    @Test
    void 검색어의_공백과_구분자는_무시된다() {
        when(cityRepository.searchByName(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        service.searchCities(null, "New York", null);

        verify(cityRepository).searchByName("%newyork%", "newyork%", Pageable.ofSize(10));
    }

    @Test
    void 결과가_없으면_빈_배열을_돌려준다() {
        when(cityRepository.searchByName(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service.searchCities(null, "없는도시", null).cities()).isEmpty();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "-.-"})
    void 검색_키가_남지_않는_입력은_명세의_400이다(String keyword) {
        assertThatThrownBy(() -> service.searchCountries(keyword, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_COUNTRY_KEYWORD);

        assertThatThrownBy(() -> service.searchCities(null, keyword, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_CITY_KEYWORD);

        verifyNoInteractions(countryRepository, cityRepository);
    }

    @Test
    void 지나치게_긴_검색어는_조회하지_않고_400이다() {
        assertThatThrownBy(() -> service.searchCountries("가".repeat(51), null))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(countryRepository);
    }

    @Test
    void limit은_허용_범위로_맞춰지고_거절하지_않는다() {
        when(cityRepository.searchByName(anyString(), anyString(), any(Pageable.class)))
                .thenReturn(List.of());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);

        service.searchCities(null, "서울", 0);
        service.searchCities(null, "서울", 999);
        service.searchCities(null, "서울", 5);

        verify(cityRepository, org.mockito.Mockito.times(3))
                .searchByName(anyString(), anyString(), captor.capture());
        assertThat(captor.getAllValues()).extracting(Pageable::getPageSize)
                .containsExactly(1, 50, 5);
    }
}
