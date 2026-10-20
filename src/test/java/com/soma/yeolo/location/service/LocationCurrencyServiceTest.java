package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CountryRepository;
import com.soma.yeolo.location.service.CountryCurrencyReader.CountryCurrency;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 국가 데이터셋의 통화 코드·기호 조회 (#100). */
@ExtendWith(MockitoExtension.class)
class LocationCurrencyServiceTest {

    @Mock
    private CountryRepository countryRepository;

    @InjectMocks
    private LocationCurrencyService service;

    @Test
    void 국가명으로_통화_코드와_기호를_찾는다() {
        when(countryRepository.findByAnySearchName("일본"))
                .thenReturn(Optional.of(CountryEntity.of("JP", "일본", "Japan", "1850147", "JPY", "¥")));

        assertThat(service.findCurrency(" 일본 ")).contains(new CountryCurrency("JPY", "¥"));
    }

    /** 코스의 목적지 국가명은 사용자 언어로 저장된다 — 영문명으로도 같은 통화를 찾아야 exRateIcon 이 비지 않는다. */
    @Test
    void 영문_국가명으로도_통화를_찾는다() {
        when(countryRepository.findByAnySearchName("japan"))
                .thenReturn(Optional.of(CountryEntity.of("JP", "일본", "Japan", "1850147", "JPY", "¥")));

        assertThat(service.findCurrency("Japan")).contains(new CountryCurrency("JPY", "¥"));
    }

    @Test
    void 기호가_없으면_코드를_기호로_쓴다() {
        when(countryRepository.findByAnySearchName("파나마"))
                .thenReturn(Optional.of(CountryEntity.of("PA", "파나마", "Panama", null, "PAB", null)));

        assertThat(service.findCurrency("파나마")).contains(new CountryCurrency("PAB", "PAB"));
    }

    @Test
    void 통화가_없는_영토나_없는_국가는_빈_값이다() {
        when(countryRepository.findByAnySearchName("남극대륙"))
                .thenReturn(Optional.of(CountryEntity.of("AQ", "남극 대륙", "Antarctica")));
        when(countryRepository.findByAnySearchName("없는나라")).thenReturn(Optional.empty());

        assertThat(service.findCurrency("남극 대륙")).isEmpty();
        assertThat(service.findCurrency("없는나라")).isEmpty();
    }
}
