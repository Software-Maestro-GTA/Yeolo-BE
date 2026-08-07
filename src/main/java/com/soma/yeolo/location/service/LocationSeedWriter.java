package com.soma.yeolo.location.service;

import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
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
 * <p>테이블 하나를 통째로 갈아 끼우므로 삭제와 삽입이 <b>한 트랜잭션</b> 안에 있어야 한다. 그래야
 * 롤링 배포 중 이 작업이 도는 동안에도 다른 파드가 옛 데이터를 온전히 서빙한다(중간 상태가 보이지 않음).
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
     * 국가 데이터셋을 통째로 교체한다.
     *
     * @param file 열 순서 {@code countryId, nameKo}
     */
    @Transactional
    public void replaceCountries(LocationSeedFile file) {
        if (alreadySeeded(LocationSeedLoader.COUNTRIES, file.version())) {
            return;
        }
        jdbcTemplate.update("delete from countries");
        batchInsert(COUNTRY_INSERT, file.rows(), row -> {
            CountryEntity country = CountryEntity.of(row[0], row[1]);
            return new Object[]{country.getCountryId(), country.getNameKo(),
                    country.getSearchName(), country.getSearchChosung()};
        });
        markSeeded(LocationSeedLoader.COUNTRIES, file.version());
        log.info("국가 기준 데이터 적재 완료: {}건 (version={})", file.rows().size(), file.version());
    }

    /**
     * 도시 데이터셋을 통째로 교체한다.
     *
     * @param file         열 순서 {@code cityId, nameKo, countryId, population}
     * @param countryNames 국가 코드 → 한국어 국가명. 도시 행에 비정규화해 함께 넣는다
     * @param force        버전이 같아도 다시 적재한다. 국가 데이터셋이 바뀌어 비정규화된
     *                     {@code countryNameKo}를 갱신해야 할 때 켠다
     */
    @Transactional
    public void replaceCities(LocationSeedFile file, Map<String, String> countryNames, boolean force) {
        if (!force && alreadySeeded(LocationSeedLoader.CITIES, file.version())) {
            return;
        }
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
     * 트랜잭션 안에서 버전을 한 번 더 확인한다.
     *
     * <p>파드가 여러 개면 새 데이터셋이 나간 배포에서 모두가 동시에 "재적재해야 한다"고 판단한다.
     * READ COMMITTED 아래에서 먼저 커밋한 파드의 결과가 여기 보이므로, 뒤따라온 파드는 3만 건을
     * 다시 넣지 않고 빠져나간다. (완전한 상호배제는 아니다 — 앞선 적재가 아직 커밋 전이면 중복
     * 작업이 일어난다. 결과는 같고 데이터셋이 바뀐 배포에서만 생기는 일이라 락까지 두지 않는다.)
     */
    private boolean alreadySeeded(String dataset, String version) {
        return currentVersion(dataset).map(version::equals).orElse(false);
    }

    /** 적재 버전 기록. UPSERT 문법은 DB마다 달라서 삭제 후 삽입으로 둔다(같은 트랜잭션 안이다). */
    private void markSeeded(String dataset, String version) {
        jdbcTemplate.update("delete from location_seed_state where dataset = ?", dataset);
        jdbcTemplate.update("insert into location_seed_state (dataset, version) values (?, ?)",
                dataset, version);
    }
}
