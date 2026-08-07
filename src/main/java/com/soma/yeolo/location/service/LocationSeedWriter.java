package com.soma.yeolo.location.service;

import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기준 데이터셋을 테이블에 적재한다 (API-LOC-1 / API-LOC-2).
 *
 * <p><b>JPA가 아니라 JdbcTemplate으로 쓴다.</b> 국가·도시는 식별자가 파일에서 정해져 오는(assigned id)
 * 수만 건짜리 벌크다. JPA {@code save}는 assigned id를 보면 "이미 있는 행일 수 있다"고 판단해 행마다
 * SELECT를 먼저 날린다 — 적재 한 번에 수만 번의 왕복이 생긴다. 배치 INSERT면 한 자릿수 초에 끝난다.
 * 조회 경로는 그대로 JPA를 쓰고, 이 클래스만 벌크 적재를 위해 SQL을 쓴다.
 *
 * <p>테이블을 통째로 갈아 끼우므로 삭제와 삽입이 <b>한 트랜잭션</b> 안에 있어야 한다. 그래야
 * 롤링 배포 중 이 작업이 도는 동안에도 다른 파드가 옛 데이터를 온전히 서빙한다(중간 상태가 보이지 않음).
 * 국가와 도시도 서로 나뉘지 않는다 — 이유는 {@link #replace}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocationSeedWriter {

    /** 한 번에 보내는 배치 크기. 너무 크면 드라이버 버퍼만 키우고 이득이 없다. */
    private static final int BATCH_SIZE = 1_000;

    private static final String COUNTRY_INSERT = """
            insert into countries (country_id, name_ko, search_name, search_chosung)
            values (?, ?, ?, ?)
            """;

    private static final String CITY_INSERT = """
            insert into cities
                (city_id, name_ko, search_name, search_chosung, country_id, country_name_ko, population)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    /** 마지막으로 적재한 데이터셋 버전. 한 번도 적재하지 않았으면 빈 값. */
    public Optional<String> currentVersion(String dataset) {
        return jdbcTemplate
                .query("select version from location_seed_state where dataset = ?",
                        (rs, rowNum) -> rs.getString(1), dataset)
                .stream()
                .findFirst();
    }

    /**
     * 재적재가 필요한 데이터셋을 <b>하나의 트랜잭션에서</b> 교체한다.
     *
     * <p>국가와 도시를 나눠 커밋하면 안 된다. 도시 행은 {@code country_name_ko}를 비정규화해 들고
     * 있어서, 국가만 커밋된 뒤 도시 적재가 끊기면(파드 종료·statement timeout) 다음 부팅에는 국가가
     * 이미 "최신"으로 기록돼 있어 <b>둘 다 건너뛴다</b> — 낡은 국가명 사본이 영구히 남고 복구하려면
     * {@code location_seed_state}를 손으로 고쳐야 한다. 한 트랜잭션이면 실패 시 통째로 롤백되고
     * 다음 부팅이 다시 시도한다.
     *
     * <p>트랜잭션 진입 후 버전을 다시 확인하므로, 같은 배포에서 앞선 파드가 이미 끝냈으면 아무 일도
     * 하지 않는다 — 파드 수만큼 3만 건 적재가 반복되며 기동이 줄서는 것을 막는다.
     *
     * @param countries      국가 데이터셋 (열 순서 {@code countryId, nameKo})
     * @param reloadCountries 국가 재적재가 필요하다고 판단됐는지
     * @param cities         도시 데이터셋 (열 순서 {@code cityId, nameKo, countryId, population}).
     *                       파일이 없으면 {@code null}
     * @param reloadCities   도시 재적재가 필요하다고 판단됐는지
     */
    @Transactional
    public void replace(LocationSeedFile countries, boolean reloadCountries,
                        LocationSeedFile cities, boolean reloadCities) {
        boolean seedCountries = reloadCountries
                && !alreadySeeded(LocationSeedLoader.COUNTRIES, countries.version());
        // 국가를 새로 넣으면 도시의 국가명 사본도 같이 갱신해야 하므로, 도시 버전이 그대로여도 다시 넣는다.
        boolean seedCities = reloadCities && cities != null
                && (seedCountries || !alreadySeeded(LocationSeedLoader.CITIES, cities.version()));

        if (!seedCountries && !seedCities) {
            log.info("다른 인스턴스가 이미 적재를 마쳤다. 이번 적재는 건너뛴다.");
            return;
        }
        if (seedCountries) {
            replaceCountries(countries);
        }
        if (seedCities) {
            replaceCities(cities, countryNames(countries));
        }
    }

    /** 국가 코드 → 한국어 국가명. 도시 행에 비정규화해 넣을 값을 국가 데이터셋에서 만든다. */
    private Map<String, String> countryNames(LocationSeedFile countries) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String[] row : countries.rows()) {
            names.put(row[0], row[1]);
        }
        return names;
    }

    private void replaceCountries(LocationSeedFile file) {
        jdbcTemplate.update("delete from countries");
        batchInsert(COUNTRY_INSERT, file.rows(), row -> {
            CountryEntity country = CountryEntity.of(row[0], row[1]);
            return new Object[]{country.getCountryId(), country.getNameKo(),
                    country.getSearchName(), country.getSearchChosung()};
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

    private void batchInsert(String sql, List<String[]> rows, Function<String[], Object[]> toArgs) {
        for (int from = 0; from < rows.size(); from += BATCH_SIZE) {
            List<Object[]> batch = rows.subList(from, Math.min(from + BATCH_SIZE, rows.size()))
                    .stream().map(toArgs).toList();
            jdbcTemplate.batchUpdate(sql, batch);
        }
    }

    /**
     * 이 데이터셋이 이미 해당 버전으로 적재돼 있는지. {@link #replace}가 트랜잭션 안에서 호출한다.
     *
     * <p>READ COMMITTED 아래에서 먼저 커밋한 파드의 결과가 보이므로, 뒤따라온 파드는 3만 건을 다시
     * 넣지 않고 빠져나간다. (완전한 상호배제는 아니다 — 앞선 적재가 아직 커밋 전이면 중복 작업이
     * 일어나고, 나중 쪽은 PK 충돌로 롤백된다. {@code LocationSeedLoader}가 그 경우를 구분해 처리한다.)
     */
    public boolean alreadySeeded(String dataset, String version) {
        return currentVersion(dataset).map(version::equals).orElse(false);
    }

    /** 적재 버전 기록. UPSERT 문법은 DB마다 달라서 삭제 후 삽입으로 둔다(같은 트랜잭션 안이다). */
    private void markSeeded(String dataset, String version) {
        jdbcTemplate.update("delete from location_seed_state where dataset = ?", dataset);
        jdbcTemplate.update("insert into location_seed_state (dataset, version) values (?, ?)",
                dataset, version);
    }
}
