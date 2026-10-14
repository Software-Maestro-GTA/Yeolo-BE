package com.soma.yeolo.location.service;

import com.soma.yeolo.location.domain.SearchKeys;
import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CityImageEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityImageRepository;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 도시 대표 사진 조회 ({@link LocationImageReader} 구현). 데이터셋 근거와 폴백 규칙은 인터페이스와
 * {@link CityImageEntity} 설명을 본다. 도시 이름은 완전 일치 → 접두어 일치 순으로 찾는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationImageService implements LocationImageReader {

    private final CountryRepository countryRepository;
    private final CityRepository cityRepository;
    private final CityImageRepository cityImageRepository;
    private final LocationImageProperties properties;

    @Override
    public Optional<String> findCityImageUrl(String countryName, String cityName) {
        if (!StringUtils.hasText(properties.baseUrl())) {
            // 설정이 없으면 사진은 전부 빈 값이다. 매 호출 경고하면 로그가 넘치므로 디버그로 둔다.
            log.debug("location.images.base-url 이 비어 있어 도시 사진을 내려줄 수 없다");
            return Optional.empty();
        }
        Optional<CountryEntity> country = countryRepository.findBySearchName(SearchKeys.normalize(countryName));
        if (country.isEmpty()) {
            return Optional.empty();
        }
        String countryId = country.get().getCountryId();
        return cityImage(countryId, cityName)
                .or(() -> capitalImage(country.get()))
                .or(() -> largestCityImage(countryId))
                .map(this::toUrl);
    }

    private Optional<CityImageEntity> cityImage(String countryId, String cityName) {
        String key = SearchKeys.normalize(cityName);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        // 완전 일치 → 접두어 일치("부산" → "부산광역시", 인구 최다). 접두어 단계가 없으면 흔한 입력이
        // 전부 수도 사진으로 떨어져 "부산"에 서울 사진이 붙는다.
        return cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc(countryId, key)
                .or(() -> cityRepository.findFirstByCountryIdAndSearchNameStartingWithOrderByPopulationDesc(countryId, key))
                .flatMap(city -> cityImageRepository.findById(city.getCityId()));
    }

    private Optional<CityImageEntity> capitalImage(CountryEntity country) {
        return Optional.ofNullable(country.getCapitalCityId()).flatMap(cityImageRepository::findById);
    }

    private Optional<CityImageEntity> largestCityImage(String countryId) {
        return cityRepository.findWithImageByCountryIdOrderByPopulationDesc(countryId, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(CityEntity::getCityId)
                .flatMap(cityImageRepository::findById);
    }

    private String toUrl(CityImageEntity image) {
        String base = properties.baseUrl().strip();
        base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return base + "/" + image.getS3Key();
    }
}
