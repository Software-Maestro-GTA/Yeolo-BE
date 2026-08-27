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
 * 부팅 시 국가·도시 기준 데이터셋을 적재한다 (API-LOC-1 / API-LOC-2).
 *
 * <p>데이터셋은 저장소에 파일로 들어 있고(CLDR 한국어 지역명 + GeoNames {@code cities15000}),
 * 생성 방법은 {@code docs/location-dataset.md}에 있다. 파일이 근거이므로 DB는 파생물이다 —
 * 파일이 바뀌면(= {@code #version}이 바뀌면) 통째로 다시 적재한다.
 *
 * <p>평시 부팅에서는 <b>각 파일의 헤더 한 줄과 상태 조회 두 번</b>으로 끝난다. 전체 파싱은 실제로
 * 재적재가 필요할 때만 한다.
 *
 * <p>{@link ApplicationRunner}라서 <b>readiness 이전</b>에 끝난다. 트래픽이 들어오는 시점에는
 * 적재가 이미 완료돼 있다.
 *
 * <p><b>적재 실패는 앱을 죽이지 않는다.</b> 자동완성이 안 되는 것과 서비스 전체가 안 뜨는 것은
 * 무게가 다르다 — 로그인·코스 조회까지 같이 멈출 이유가 없다. 대신 ERROR로 남겨 배포에서 드러나게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationSeedLoader implements ApplicationRunner {

    static final String COUNTRIES = "countries";
    static final String CITIES = "cities";

    /** {@code countryId, nameKo} */
    private static final int COUNTRY_COLUMNS = 2;

    /** {@code cityId, nameKo, countryId, population} */
    private static final int CITY_COLUMNS = 4;

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
            // 자동완성만 비게 되고 나머지 기능은 정상 동작한다. 기동은 막지 않되 확실히 드러낸다.
            log.error("국가·도시 기준 데이터 적재에 실패했다. 자동완성 API가 빈 결과를 돌려준다.", e);
        }
    }

    private void seed() throws IOException {
        Optional<String> countriesVersion = readVersion(properties.countriesPath());
        if (countriesVersion.isEmpty()) {
            log.warn("국가 기준 데이터셋이 없다: {}. 자동완성 적재를 중단한다.", properties.countriesPath());
            return;
        }
        Optional<String> citiesVersion = readVersion(properties.citiesPath());
        if (citiesVersion.isEmpty()) {
            log.warn("도시 기준 데이터셋이 없다: {}", properties.citiesPath());
        }

        boolean countriesStale = needsReload(COUNTRIES, countriesVersion.get());
        // 국가명은 cities 행에 비정규화돼 있다(CityEntity 참고). 국가를 새로 적재하면 그 사본이
        // 낡으므로 도시도 함께 다시 넣는다 — 버전을 따로 보면 도시가 옛 국가명을 계속 광고한다.
        boolean citiesStale = citiesVersion.isPresent()
                && (countriesStale || needsReload(CITIES, citiesVersion.get()));

        if (!countriesStale && !citiesStale) {
            log.info("국가·도시 기준 데이터가 최신이다 (countries={}, cities={})",
                    countriesVersion.get(), citiesVersion.orElse("없음"));
            return;
        }

        // 여기서부터는 전체 파싱이 필요하다. 국가는 도시의 countryNameKo 공급원이기도 해서
        // 도시만 재적재하는 경우에도 읽는다.
        LocationSeedFile countries = read(properties.countriesPath(), COUNTRY_COLUMNS).orElseThrow();
        LocationSeedFile cities = citiesStale
                ? read(properties.citiesPath(), CITY_COLUMNS).orElse(null)
                : null;

        try {
            writer.replace(countries, countriesStale, cities, citiesStale);
        } catch (DataIntegrityViolationException e) {
            handleIntegrityViolation(e, countriesVersion.get(), citiesVersion);
        }
    }

    /**
     * 적재 중 무결성 위반이 났을 때, <b>경합인지 데이터 결함인지 가려낸다.</b>
     *
     * <p>파드가 여러 개면 뒤늦게 커밋하려던 쪽이 먼저 커밋된 행과 PK 충돌로 롤백된다 — 데이터는
     * 앞선 적재로 이미 올바르므로 장애가 아니고, ERROR로 올리면 배포마다 헛경보가 된다.
     *
     * <p>그런데 <b>데이터셋 결함도 같은 예외로 온다</b>: 재생성된 파일에 {@code geonameid}가 중복이거나,
     * {@code country_id}가 컬럼 길이를 넘거나, NOT NULL을 위반하는 경우다. 이때는 테이블이 빈 채로
     * 남는데 "다른 인스턴스가 먼저 적재했다"고 찍으면 담당자를 엉뚱한 곳으로 보낸다.
     *
     * <p>구분 기준은 <b>적재 상태가 실제로 최신이 됐는지</b>다. 다른 파드가 마쳤다면 상태 행의 버전이
     * 파일과 같다. 그렇지 않으면 아무도 적재하지 못한 것이므로 예외를 그대로 올려 ERROR로 드러낸다.
     */
    private void handleIntegrityViolation(DataIntegrityViolationException e,
                                          String countriesVersion, Optional<String> citiesVersion) {
        boolean seededElsewhere = writer.alreadySeeded(COUNTRIES, countriesVersion)
                && citiesVersion.map(v -> writer.alreadySeeded(CITIES, v)).orElse(true);
        if (!seededElsewhere) {
            throw e;
        }
        log.warn("다른 인스턴스가 먼저 기준 데이터를 적재했다. 이번 적재는 건너뛴다.", e);
    }

    /** 데이터셋 파일의 버전만 읽는다. 파일이 없으면 빈 값 — 적재만 건너뛰고 기동은 계속한다. */
    private Optional<String> readVersion(String classpathLocation) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(LocationSeedFile.parseVersion(in));
        }
    }

    private Optional<LocationSeedFile> read(String classpathLocation, int columns) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(LocationSeedFile.parse(in, columns));
        }
    }

    private boolean needsReload(String dataset, String version) {
        return !writer.alreadySeeded(dataset, version);
    }
}
