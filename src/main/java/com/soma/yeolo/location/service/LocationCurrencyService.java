package com.soma.yeolo.location.service;

import com.soma.yeolo.location.domain.SearchKeys;
import com.soma.yeolo.location.repository.CountryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** {@link CountryCurrencyReader} 구현 — {@code countries.currency_code/symbol} 조회. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationCurrencyService implements CountryCurrencyReader {

    private final CountryRepository countryRepository;

    @Override
    public Optional<CountryCurrency> findCurrency(String countryName) {
        return countryRepository.findBySearchName(SearchKeys.normalize(countryName))
                .filter(country -> country.getCurrencyCode() != null)
                .map(country -> new CountryCurrency(country.getCurrencyCode(),
                        country.getCurrencySymbol() == null ? country.getCurrencyCode() : country.getCurrencySymbol()));
    }
}
