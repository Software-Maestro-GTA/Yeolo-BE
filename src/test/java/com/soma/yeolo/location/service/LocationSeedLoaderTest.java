package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.soma.yeolo.location.domain.LikePatterns;
import com.soma.yeolo.location.entity.CountryEntity;
import com.soma.yeolo.location.repository.CityImageRepository;
import com.soma.yeolo.location.repository.CityRepository;
import com.soma.yeolo.location.repository.CountryRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
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

    @Autowired
    private CityImageRepository cityImageRepository;

    private LocationSeedWriter writer;
    private LocationSeedLoader loader;

    private static final Pageable TOP_10 = Pageable.ofSize(10);

    @BeforeEach
    void setUp() {
        writer = new LocationSeedWriter(new JdbcTemplate(dataSource));
        loader = new LocationSeedLoader(
                new LocationSeedProperties(true,
                        "data/locations/countries.tsv", "data/locations/cities.tsv",
                        "data/locations/city_images.tsv"),
                writer);
    }

    /** 실제 데이터셋 파일을 파싱한다 — 적재기를 거치지 않고 writer 를 직접 부르는 테스트용. */
    private LocationSeedFile parseResource(String location, int columns) {
        return parseResource(location, columns, columns);
    }

    private LocationSeedFile parseResource(String location, int columns, int requiredColumns) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            return LocationSeedFile.parse(in, columns, requiredColumns);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private LocationSeedFile countriesFile() {
        return parseResource("data/locations/countries.tsv", 5, 2);
    }

    private LocationSeedFile citiesFile() {
        return parseResource("data/locations/cities.tsv", 4);
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
    void 국가의_수도와_통화가_실데이터에서_적재된다() {
        loader.run(null);

        CountryEntity japan = countryRepository.findById("JP").orElseThrow();
        assertThat(japan.getCapitalCityId()).isEqualTo("1850147");
        assertThat(japan.getCurrencyCode()).isEqualTo("JPY");
        assertThat(japan.getCurrencySymbol()).isEqualTo("¥");
        // 통화가 없는 영토(남극)는 비어 있어도 적재가 실패하지 않는다.
        assertThat(countryRepository.findById("AQ")).map(CountryEntity::getCurrencyCode).isEmpty();
    }

    @Test
    void 도시_접두어_검색_인수기준이_실데이터에서_동작한다() {
        loader.run(null);

        var found = cityRepository.searchByPrefix(LikePatterns.prefix("서울"));
        assertThat(found).isNotEmpty();
        assertThat(found).first().satisfies(city -> {
            assertThat(city.getCountryId()).isEqualTo("KR");
            assertThat(city.getCountryNameKo()).isEqualTo("대한민국");
        });
        assertThat(countryRepository.findBySearchName("대한민국")).map(CountryEntity::getCountryId).contains("KR");
    }

    @Test
    void 후보가_없으면_빈_목록이다() {
        loader.run(null);

        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("존재하지않는도시명"))).isEmpty();
    }

    /** 도시 사진은 도시 데이터셋에 있는 도시만 들어온다 — 사진만 남은 고아 행은 조회 경로가 없다. */
    @Test
    void 도시_사진이_적재되고_도시에_없는_사진은_들어오지_않는다() {
        loader.run(null);

        assertThat(cityImageRepository.count()).isGreaterThan(0);
        Long orphans = new JdbcTemplate(dataSource).queryForObject("""
                select count(*) from city_images i
                where not exists (select 1 from cities c where c.city_id = i.city_id)
                """, Long.class);
        assertThat(orphans).isZero();
        // 도쿄는 데이터셋에 반드시 있어야 하는 대표 도시다 — 사진 선정 규칙이 바뀌어 빠지면 여기서 드러난다.
        assertThat(cityImageRepository.findById("1850147")).isPresent();
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
    void 국가만_적재되고_도시가_실패하면_다음_부팅이_둘_다_다시_넣는다() {
        loader.run(null);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 국가 데이터셋만 새 버전이 나간 뒤, 도시 적재 도중 파드가 죽은 상황을 흉내낸다.
        // 두 적재가 한 트랜잭션이 아니었다면 국가만 '최신'으로 기록돼 다음 부팅이 둘 다 건너뛰고,
        // 도시의 국가명 사본은 영원히 낡은 채로 남는다.
        jdbc.update("update location_seed_state set version = ? where dataset = ?", "옛버전", "countries");
        jdbc.update("update cities set country_name_ko = ?", "낡은국가명");
        jdbc.update("delete from countries");

        loader.run(null);

        assertThat(countryRepository.count()).isGreaterThan(200);
        Long stale = jdbc.queryForObject(
                "select count(*) from cities where country_name_ko = ?", Long.class, "낡은국가명");
        assertThat(stale).isZero();
    }

    @Test
    void 다른_인스턴스가_이미_적재했으면_아무것도_하지_않는다() {
        loader.run(null);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // 호출자(로더)는 '재적재 필요'로 판단해 둘 다 true 로 넘기지만, 트랜잭션에 들어간 시점엔
        // 다른 파드가 이미 같은 버전으로 마쳐둔 상황이다 — 3만 건을 다시 넣지 않고 빠져나가야 한다.
        String version = jdbc.queryForObject(
                "select version from location_seed_state where dataset = ?", String.class, "countries");
        long citiesBefore = cityRepository.count();

        writer.replace(countriesFile(), true, citiesFile(), true, null, false);

        assertThat(cityRepository.count()).isEqualTo(citiesBefore);
        assertThat(jdbc.queryForObject("select version from location_seed_state where dataset = ?",
                String.class, "countries")).isEqualTo(version);
    }

    @Test
    void 데이터셋_결함으로_적재가_실패하면_경합이_아니라_예외로_드러난다() {
        // city_id 가 중복인 데이터셋 — 경합이 아니라 파일 자체의 결함이다.
        LocationSeedFile broken = LocationSeedFile.parse(new ByteArrayInputStream("""
                #version\tbroken-v1
                1\t중복도시\tKR\t100
                1\t중복도시\tKR\t100
                """.getBytes(StandardCharsets.UTF_8)), 4);

        assertThatThrownBy(() -> writer.replace(countriesFile(), true, broken, true, null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
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
