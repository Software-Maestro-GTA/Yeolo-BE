package com.soma.yeolo.location.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * 기동 시 국가·도시·도시 사진 기준 데이터를 적재한다 (docs/location-dataset.md §적재 동작).
 *
 * <p>평시 부팅은 각 파일의 {@code #version} 헤더 한 줄과 상태 조회만으로 끝나고, 버전이 바뀐
 * 데이터셋만 한 트랜잭션에서 통째로 교체한다. 적재 실패는 ERROR 로그만 남기고 기동을 막지 않는다 —
 * 지역 API 와 사진만 비고 나머지 기능은 정상이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationSeedLoader implements ApplicationRunner {

    static final String COUNTRIES = "countries";
    static final String CITIES = "cities";
    static final String CITY_IMAGES = "city_images";

    static final int COUNTRY_COLUMNS = 5;
    /** 국가는 식별자·이름만 필수다. 수도·통화는 없는 국가(남극 등)가 있어 비어 있을 수 있다. */
    static final int COUNTRY_REQUIRED_COLUMNS = 2;
    static final int CITY_COLUMNS = 4;
    static final int CITY_IMAGE_COLUMNS = 4;

    private final LocationSeedProperties properties;
    private final LocationSeedWriter writer;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            log.info("기준 데이터 적재를 건너뛴다 (location.seed.enabled=false)");
            return;
        }
        try {
            seed();
        } catch (Exception e) {
            log.error("국가·도시 기준 데이터 적재에 실패했다. 지역 API 가 빈 결과를 돌려준다.", e);
        }
    }

    private void seed() throws IOException {
        Optional<String> countriesVersion = readVersion(properties.countriesPath());
        if (countriesVersion.isEmpty()) {
            log.warn("국가 기준 데이터셋이 없다: {}. 적재를 중단한다.", properties.countriesPath());
            return;
        }
        Optional<String> citiesVersion = readVersion(properties.citiesPath());
        if (citiesVersion.isEmpty()) {
            log.warn("도시 기준 데이터셋이 없다: {}", properties.citiesPath());
        }
        Optional<String> imagesVersion = readVersion(properties.cityImagesPath());
        if (imagesVersion.isEmpty()) {
            log.warn("도시 사진 데이터셋이 없다: {}. 추천 도시 사진이 비게 된다.", properties.cityImagesPath());
        }

        boolean countriesStale = needsReload(COUNTRIES, countriesVersion.get());
        // 국가명은 cities 행에 비정규화돼 있다(CityEntity 참고). 국가를 새로 적재하면 그 사본이
        // 낡으므로 도시도 함께 다시 넣는다 — 버전을 따로 보면 도시가 옛 국가명을 계속 광고한다.
        boolean citiesStale = citiesVersion.isPresent()
                && (countriesStale || needsReload(CITIES, citiesVersion.get()));
        // 사진은 도시 테이블을 기준으로 고아 행을 걸러 넣는다. 도시가 다시 적재되면(새 도시가 들어오면)
        // 전에 걸러졌던 사진이 들어와야 하므로, 사진 버전이 그대로여도 도시와 함께 다시 넣는다.
        boolean imagesStale = imagesVersion.isPresent()
                && (citiesStale || needsReload(CITY_IMAGES, imagesVersion.get()));
        if (!countriesStale && !citiesStale && !imagesStale) {
            log.info("국가·도시·사진 기준 데이터가 최신이다 (countries={}, cities={}, city_images={})",
                    countriesVersion.get(), citiesVersion.orElse("없음"), imagesVersion.orElse("없음"));
            return;
        }

        // 여기서부터는 전체 파싱이 필요하다. 국가는 도시의 countryNameKo 공급원이기도 해서
        // 도시만 재적재하는 경우에도 읽는다.
        LocationSeedFile countries = read(properties.countriesPath(), COUNTRY_COLUMNS, COUNTRY_REQUIRED_COLUMNS)
                .orElseThrow();
        LocationSeedFile cities = citiesStale
                ? read(properties.citiesPath(), CITY_COLUMNS, CITY_COLUMNS).orElse(null)
                : null;
        LocationSeedFile images = imagesStale
                ? read(properties.cityImagesPath(), CITY_IMAGE_COLUMNS, CITY_IMAGE_COLUMNS).orElse(null)
                : null;
        try {
            writer.replace(countries, countriesStale, cities, citiesStale, images, imagesStale);
        } catch (DataIntegrityViolationException e) {
            handleIntegrityViolation(e, countriesVersion.get(), citiesVersion, imagesVersion);
        }
    }

    private void handleIntegrityViolation(DataIntegrityViolationException e, String countriesVersion,
                                          Optional<String> citiesVersion, Optional<String> imagesVersion) {
        boolean seededElsewhere = writer.alreadySeeded(COUNTRIES, countriesVersion)
                && citiesVersion.map(v -> writer.alreadySeeded(CITIES, v)).orElse(true)
                && imagesVersion.map(v -> writer.alreadySeeded(CITY_IMAGES, v)).orElse(true);
        if (!seededElsewhere) {
            throw e;
        }
        log.warn("다른 인스턴스가 먼저 기준 데이터를 적재했다. 이번 적재는 건너뛴다.", e);
    }

    private Optional<String> readVersion(String classpathLocation) throws IOException {
        if (classpathLocation == null || classpathLocation.isBlank()) {
            return Optional.empty();
        }
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(LocationSeedFile.parseVersion(in));
        }
    }

    private Optional<LocationSeedFile> read(String classpathLocation, int columns, int requiredColumns)
            throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(LocationSeedFile.parse(in, columns, requiredColumns));
        }
    }

    private boolean needsReload(String dataset, String version) {
        return !writer.alreadySeeded(dataset, version);
    }
}
