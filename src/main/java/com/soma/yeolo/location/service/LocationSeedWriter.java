package com.soma.yeolo.location.service;

import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CityImageEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 데이터 테이블을 통째로 교체한다. 한 트랜잭션 안에서 delete+insert 하므로 롤링 배포 중 다른
 * 파드에 중간 상태가 보이지 않는다. JPA 대신 JdbcTemplate 배치를 쓰는 이유는 3만 행의 영속성
 * 컨텍스트 비용을 피하기 위해서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationSeedWriter {

    private static final int BATCH_SIZE = 1_000;
    private static final String COUNTRY_INSERT = """
            insert into countries
                (country_id, name_ko, search_name, search_chosung, capital_city_id, currency_code, currency_symbol)
            values (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String CITY_INSERT = """
            insert into cities
                (city_id, name_ko, search_name, search_chosung, country_id, country_name_ko, population)
            values (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String CITY_IMAGE_INSERT = """
            insert into city_images (city_id, s3_key, source, source_id)
            values (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public Optional<String> currentVersion(String dataset) {
        return jdbcTemplate
                .query("select version from location_seed_state where dataset = ?",
                        (rs, rowNum) -> rs.getString(1), dataset)
                .stream()
                .findFirst();
    }

    /**
     * 재적재가 필요한 데이터셋만 교체한다. 트랜잭션 안에서 버전을 다시 확인하므로, 다른 파드가 먼저
     * 같은 버전을 넣어 뒀으면 아무것도 하지 않는다.
     */
    @Transactional
    public void replace(LocationSeedFile countries, boolean reloadCountries,
                        LocationSeedFile cities, boolean reloadCities,
                        LocationSeedFile images, boolean reloadImages) {
        boolean seedCountries = reloadCountries
                && !alreadySeeded(LocationSeedLoader.COUNTRIES, countries.version());
        // 국가를 새로 넣으면 도시의 국가명 사본도 같이 갱신해야 하므로, 도시 버전이 그대로여도 다시 넣는다.
        boolean seedCities = reloadCities && cities != null
                && (seedCountries || !alreadySeeded(LocationSeedLoader.CITIES, cities.version()));
        // 도시를 새로 넣으면 고아 판정이 달라지므로 사진 버전이 그대로여도 다시 넣는다(로더와 같은 규칙).
        boolean seedImages = reloadImages && images != null
                && (seedCities || !alreadySeeded(LocationSeedLoader.CITY_IMAGES, images.version()));
        if (!seedCountries && !seedCities && !seedImages) {
            log.info("다른 인스턴스가 이미 적재를 마쳤다. 이번 적재는 건너뛴다.");
            return;
        }
        if (seedCountries) {
            replaceCountries(countries);
        }
        if (seedCities) {
            replaceCities(cities, countryNames(countries));
        }
        if (seedImages) {
            replaceCityImages(images);
        }
    }

    private Map<String, String> countryNames(LocationSeedFile countries) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String[] row : countries.rows()) {
            names.put(row[0], row[1]);
        }
        return names;
    }

    private void replaceCountries(LocationSeedFile file) {
        // search_name 은 국가명 완전 일치 조회(LOC-2·LOC-3·사진)의 유일 키다. 정규화 후 겹치는 이름이
        // 있으면 조회가 500(IncorrectResultSize)이 되므로 적재 자체를 거부한다(트랜잭션 롤백 → 옛 데이터 유지).
        Set<String> seen = new java.util.HashSet<>();
        for (String[] row : file.rows()) {
            String key = com.soma.yeolo.location.domain.SearchKeys.normalize(row[1]);
            if (!seen.add(key)) {
                throw new IllegalStateException("국가 데이터셋에 정규화 후 같은 이름이 둘 있다: " + row[1]);
            }
        }
        jdbcTemplate.update("delete from countries");
        batchInsert(COUNTRY_INSERT, file.rows(), row -> {
            CountryEntity country = CountryEntity.of(row[0], row[1], row[2], row[3], row[4]);
            return new Object[]{country.getCountryId(), country.getNameKo(),
                    country.getSearchName(), country.getSearchChosung(),
                    country.getCapitalCityId(), country.getCurrencyCode(), country.getCurrencySymbol()};
        });
        markSeeded(LocationSeedLoader.COUNTRIES, file.version());
        log.info("국가 기준 데이터 적재 완료: {}건 (version={})", file.rows().size(), file.version());
    }

    private void replaceCities(LocationSeedFile file, Map<String, String> countryNames) {
        jdbcTemplate.update("delete from cities");
        List<String[]> rows = file.rows().stream()
                // 국가 기준 정보에 없는 국가의 도시는 버린다. countryNameKo 가 not null 이기도 하지만,
                // 애초에 FE가 국가 목록에서 고를 수 없는 국가의 도시를 후보로 보여줄 이유가 없다.
                .filter(row -> countryNames.containsKey(row[2]))
                .toList();
        int skipped = file.rows().size() - rows.size();
        if (skipped > 0) {
            log.warn("국가 기준 정보에 없는 국가의 도시 {}건을 건너뛴다", skipped);
        }
        batchInsert(CITY_INSERT, rows, row -> {
            CityEntity city = CityEntity.of(row[0], row[1], row[2], countryNames.get(row[2]),
                    Long.parseLong(row[3]));
            return new Object[]{city.getCityId(), city.getNameKo(), city.getSearchName(),
                    city.getSearchChosung(), city.getCountryId(), city.getCountryNameKo(),
                    city.getPopulation()};
        });
        markSeeded(LocationSeedLoader.CITIES, file.version());
        log.info("도시 기준 데이터 적재 완료: {}건 (version={})", rows.size(), file.version());
    }

    private void replaceCityImages(LocationSeedFile file) {
        jdbcTemplate.update("delete from city_images");
        // 도시 데이터셋에 없는 도시의 사진은 버린다 — 도시가 빠졌는데 사진만 남으면 조회 경로가 없다.
        Set<String> cityIds = jdbcTemplate.query("select city_id from cities", (rs, n) -> rs.getString(1))
                .stream().collect(Collectors.toSet());
        List<String[]> rows = file.rows().stream().filter(row -> cityIds.contains(row[0])).toList();
        int skipped = file.rows().size() - rows.size();
        if (skipped > 0) {
            log.warn("도시 기준 정보에 없는 도시의 사진 {}건을 건너뛴다", skipped);
        }
        batchInsert(CITY_IMAGE_INSERT, rows, row -> {
            CityImageEntity image = CityImageEntity.of(row[0], row[1], row[2], row[3]);
            return new Object[]{image.getCityId(), image.getS3Key(), image.getSource(), image.getSourceId()};
        });
        markSeeded(LocationSeedLoader.CITY_IMAGES, file.version());
        log.info("도시 사진 데이터 적재 완료: {}건 (version={})", rows.size(), file.version());
    }

    private void batchInsert(String sql, List<String[]> rows, Function<String[], Object[]> toArgs) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<Object[]> batch = rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()))
                    .stream().map(toArgs).toList();
            jdbcTemplate.batchUpdate(sql, batch);
        }
    }

    public boolean alreadySeeded(String dataset, String version) {
        return currentVersion(dataset).map(version::equals).orElse(false);
    }

    private void markSeeded(String dataset, String version) {
        jdbcTemplate.update("delete from location_seed_state where dataset = ?", dataset);
        jdbcTemplate.update("insert into location_seed_state (dataset, version) values (?, ?)",
                dataset, version);
    }
}
