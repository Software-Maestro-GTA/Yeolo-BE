package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.location.domain.LikePatterns;
import com.soma.yeolo.location.domain.SearchKeys;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 저장소에 커밋된 <b>실제 데이터셋 파일</b>을 적재해 본다 (API-LOC-1 / API-LOC-2).
 *
 * <p>데이터셋은 스크립트가 만든 산출물이라 코드 리뷰로는 깨진 줄을 못 잡는다. 형식이 어긋나면
 * 여기서 터지고, 인수 기준("{@code keyword=ㄷ} 초성 검색", "도시 후보 반환")도 픽스처가 아닌
 * 실데이터로 확인된다.
 *
 * <p>테스트가 이미 트랜잭션 안이라 적재기의 {@code @Transactional}은 여기서 의미가 없다 —
 * 확인하려는 것은 파싱·적재·재적재 판단이다.
 */
@DataJpaTest
class LocationSeedLoaderTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CityRepository cityRepository;

    private LocationSeedWriter writer;
    private LocationSeedLoader loader;

    private static final Pageable TOP_10 = Pageable.ofSize(10);

    @BeforeEach
    void setUp() {
        writer = new LocationSeedWriter(new JdbcTemplate(dataSource));
        loader = new LocationSeedLoader(
                new LocationSeedProperties(true,
                        "data/locations/countries.tsv", "data/locations/cities.tsv"),
                writer);
    }

    @Test
    void 커밋된_데이터셋이_그대로_적재된다() {
        loader.run(null);

        // ISO 국가는 250개 안팎, cities15000은 3만 개 대다. 정확한 수는 데이터셋 갱신마다 변하므로
        // "제대로 들어왔는지" 수준으로만 확인한다.
        assertThat(countryRepository.count()).isGreaterThan(200);
        assertThat(cityRepository.count()).isGreaterThan(10_000);
    }

    @Test
    void 국가_초성_검색_인수기준이_실데이터에서_동작한다() {
        loader.run(null);

        var found = countryRepository.searchByChosung(LikePatterns.prefix("ㄷ"), TOP_10);

        assertThat(found).isNotEmpty();
        assertThat(found).extracting(CountryEntity::getNameKo)
                .allSatisfy(name -> assertThat(SearchKeys.chosung(name)).startsWith("ㄷ"));
        assertThat(countryRepository.searchByChosung(LikePatterns.prefix("ㄷㅎㅁㄱ"), TOP_10))
                .extracting(CountryEntity::getCountryId)
                .containsExactly("KR");
    }

    @Test
    void 도시_검색은_소속_국가와_함께_후보를_돌려준다() {
        loader.run(null);

        var found = cityRepository.searchByName(
                LikePatterns.contains("서울"), LikePatterns.prefix("서울"), TOP_10);

        assertThat(found).isNotEmpty();
        assertThat(found).first().satisfies(city -> {
            assertThat(city.getCountryId()).isEqualTo("KR");
            assertThat(city.getCountryNameKo()).isEqualTo("대한민국");
        });
    }

    @Test
    void 후보가_없으면_빈_목록이다() {
        loader.run(null);

        assertThat(cityRepository.searchByName(
                LikePatterns.contains("존재하지않는도시명"),
                LikePatterns.prefix("존재하지않는도시명"), TOP_10)).isEmpty();
    }

    @Test
    void 버전이_같으면_다시_적재하지_않는다() {
        loader.run(null);
        long countriesAfterFirst = countryRepository.count();

        // 적재를 한 번 더 시켜도 테이블을 건드리지 않는다(delete+insert가 돌지 않는다).
        new JdbcTemplate(dataSource).update("delete from cities");
        loader.run(null);

        assertThat(countryRepository.count()).isEqualTo(countriesAfterFirst);
        assertThat(cityRepository.count()).isZero();
    }

    @Test
    void 국가만_바뀌어도_도시를_다시_적재해_국가명_사본을_갱신한다() {
        loader.run(null);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 국가 데이터셋만 새 버전이 나간 상황을 만든다(도시 버전은 그대로).
        jdbc.update("update location_seed_state set version = ? where dataset = ?", "옛버전", "countries");
        // 도시 행의 국가명 사본이 낡았다고 가정.
        jdbc.update("update cities set country_name_ko = ?", "낡은국가명");

        loader.run(null);

        Long stale = jdbc.queryForObject(
                "select count(*) from cities where country_name_ko = ?", Long.class, "낡은국가명");
        assertThat(stale).isZero();
    }

    @Test
    void 국가_기준정보에_없는_국가의_도시는_들어오지_않는다() {
        loader.run(null);

        Long orphans = new JdbcTemplate(dataSource).queryForObject("""
                select count(*) from cities c
                where not exists (select 1 from countries n where n.country_id = c.country_id)
                """, Long.class);

        assertThat(orphans).isZero();
    }
}
