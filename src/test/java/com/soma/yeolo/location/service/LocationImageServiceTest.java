package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CityImageEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityImageRepository;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

/** 도시 사진 폴백 규칙: 도시 → 수도 → 사진 있는 최다 인구 도시 → 빈 값. */
@ExtendWith(MockitoExtension.class)
class LocationImageServiceTest {

    private static final String BASE_URL = "https://cdn.example.com";
    private static final CountryEntity JAPAN = CountryEntity.of("JP", "일본", "1850147", "JPY", "¥");

    @Mock
    private CountryRepository countryRepository;

    @Mock
    private CityRepository cityRepository;

    @Mock
    private CityImageRepository cityImageRepository;

    private LocationImageService service(String baseUrl) {
        return new LocationImageService(countryRepository, cityRepository, cityImageRepository,
                new LocationImageProperties(baseUrl));
    }

    private CityEntity city(String id, String nameKo) {
        return CityEntity.of(id, nameKo, "JP", "일본", 1_000_000L);
    }

    private CityImageEntity image(String cityId) {
        return CityImageEntity.of(cityId, "locations/cities/" + cityId + ".jpg", "pixabay", "123");
    }

    @Test
    void 도시_사진이_있으면_base_url과_합쳐_돌려준다() {
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc("JP", "오사카"))
                .thenReturn(Optional.of(city("1853909", "오사카")));
        when(cityImageRepository.findById("1853909")).thenReturn(Optional.of(image("1853909")));

        assertThat(service(BASE_URL).findCityImageUrl("일본", "오사카"))
                .contains("https://cdn.example.com/locations/cities/1853909.jpg");
    }

    /** base-url 끝의 슬래시 유무로 URL 에 "//" 가 생기지 않아야 한다. */
    @Test
    void base_url_끝의_슬래시는_중복되지_않는다() {
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc("JP", "오사카"))
                .thenReturn(Optional.of(city("1853909", "오사카")));
        when(cityImageRepository.findById("1853909")).thenReturn(Optional.of(image("1853909")));

        assertThat(service(BASE_URL + "/").findCityImageUrl("일본", "오사카"))
                .contains("https://cdn.example.com/locations/cities/1853909.jpg");
    }

    /** GeoNames 한국어명은 "부산광역시"처럼 접미사를 달고 있어 "부산"은 접두어로 찾는다 — 수도로 떨어지면 안 된다. */
    @Test
    void 완전_일치가_없으면_접두어_일치_도시의_사진을_쓴다() {
        when(countryRepository.findBySearchName("대한민국"))
                .thenReturn(Optional.of(CountryEntity.of("KR", "대한민국", "1835848", "KRW", "₩")));
        when(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc("KR", "부산"))
                .thenReturn(Optional.empty());
        when(cityRepository.findFirstByCountryIdAndSearchNameStartingWithOrderByPopulationDesc("KR", "부산"))
                .thenReturn(Optional.of(CityEntity.of("1838524", "부산광역시", "KR", "대한민국", 3_400_000L)));
        when(cityImageRepository.findById("1838524")).thenReturn(Optional.of(image("1838524")));

        assertThat(service(BASE_URL).findCityImageUrl("대한민국", "부산"))
                .contains("https://cdn.example.com/locations/cities/1838524.jpg");
    }

    @Test
    void 도시_사진이_없으면_수도_사진으로_폴백한다() {
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc("JP", "노베오카"))
                .thenReturn(Optional.of(city("1855476", "노베오카")));
        when(cityImageRepository.findById("1855476")).thenReturn(Optional.empty());
        when(cityImageRepository.findById("1850147")).thenReturn(Optional.of(image("1850147")));

        assertThat(service(BASE_URL).findCityImageUrl("일본", "노베오카"))
                .contains("https://cdn.example.com/locations/cities/1850147.jpg");
    }

    @Test
    void 도시를_못_찾아도_수도_사진으로_폴백한다() {
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(JAPAN));
        when(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc("JP", "없는도시"))
                .thenReturn(Optional.empty());
        when(cityImageRepository.findById("1850147")).thenReturn(Optional.of(image("1850147")));

        assertThat(service(BASE_URL).findCityImageUrl("일본", "없는도시"))
                .contains("https://cdn.example.com/locations/cities/1850147.jpg");
    }

    @Test
    void 수도_사진도_없으면_사진이_있는_최다_인구_도시로_폴백한다() {
        CountryEntity noCapital = CountryEntity.of("JP", "일본", null, "JPY", "¥");
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(noCapital));
        when(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(cityRepository.findWithImageByCountryIdOrderByPopulationDesc(any(), any(Pageable.class)))
                .thenReturn(List.of(city("1853909", "오사카")));
        when(cityImageRepository.findById("1853909")).thenReturn(Optional.of(image("1853909")));

        assertThat(service(BASE_URL).findCityImageUrl("일본", "없는도시"))
                .contains("https://cdn.example.com/locations/cities/1853909.jpg");
    }

    @Test
    void 그_나라에_사진이_하나도_없으면_빈_값이다() {
        CountryEntity noCapital = CountryEntity.of("JP", "일본", null, "JPY", "¥");
        when(countryRepository.findBySearchName("일본")).thenReturn(Optional.of(noCapital));
        when(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(cityRepository.findWithImageByCountryIdOrderByPopulationDesc(any(), any(Pageable.class)))
                .thenReturn(List.of());

        assertThat(service(BASE_URL).findCityImageUrl("일본", "없는도시")).isEmpty();
    }

    @Test
    void 국가를_못_찾으면_빈_값이다() {
        when(countryRepository.findBySearchName("없는나라")).thenReturn(Optional.empty());

        assertThat(service(BASE_URL).findCityImageUrl("없는나라", "도쿄")).isEmpty();
        verifyNoInteractions(cityRepository, cityImageRepository);
    }

    /** 설정이 비면 조회조차 하지 않는다 — 호출자 응답은 그대로 성공하고 사진만 빈 값이다. */
    @Test
    void base_url이_비어_있으면_조회_없이_빈_값이다() {
        assertThat(service("").findCityImageUrl("일본", "오사카")).isEmpty();
        assertThat(service(null).findCityImageUrl("일본", "오사카")).isEmpty();
        verifyNoInteractions(countryRepository, cityRepository, cityImageRepository);
    }
}
