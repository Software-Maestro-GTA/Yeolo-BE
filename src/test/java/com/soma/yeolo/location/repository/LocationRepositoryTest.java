package com.soma.yeolo.location.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.location.domain.LikePatterns;
import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Pageable;

/**
 * 자동완성 커스텀 쿼리를 DB에서 검증한다 (API-LOC-1 / API-LOC-2).
 *
 * <p>리포지토리는 원칙적으로 테스트하지 않지만(docs/architecture.md §8), 여기 쿼리에는 검증할
 * 규칙이 있다 — LIKE 이스케이프, 초성 컬럼 분기, 그리고 결과 품질을 좌우하는 정렬(앞부분 일치 우선,
 * 인구순)이다. 이건 목으로는 확인되지 않는다.
 */
@DataJpaTest
class LocationRepositoryTest {

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CityRepository cityRepository;

    private static final Pageable TOP_10 = Pageable.ofSize(10);

    @BeforeEach
    void setUp() {
        countryRepository.saveAll(java.util.List.of(
                CountryEntity.of("KR", "대한민국"),
                CountryEntity.of("DK", "덴마크"),
                CountryEntity.of("DE", "독일"),
                CountryEntity.of("US", "미국"),
                CountryEntity.of("GB", "영국"),
                CountryEntity.of("DO", "도미니카 공화국")));

        cityRepository.saveAll(java.util.List.of(
                CityEntity.of("1", "서울특별시", "KR", "대한민국", 10_349_312L),
                CityEntity.of("2", "서귀포시", "KR", "대한민국", 155_691L),
                CityEntity.of("3", "부산광역시", "KR", "대한민국", 3_285_147L),
                CityEntity.of("4", "도쿄", "JP", "일본", 9_733_276L),
                CityEntity.of("5", "New York City", "US", "미국", 8_804_190L)));
    }

    @Test
    void 국가_이름_부분_일치로_찾는다() {
        var found = countryRepository.searchByName(
                LikePatterns.contains("한민"), LikePatterns.prefix("한민"), TOP_10);

        assertThat(found).extracting(CountryEntity::getCountryId).containsExactly("KR");
    }

    @Test
    void 앞부분이_일치하는_국가가_먼저_온다() {
        // "미"로 시작하는 미국이, 이름 중간에 "미"가 있는 도미니카 공화국보다 위여야 한다.
        var found = countryRepository.searchByName(
                LikePatterns.contains("미"), LikePatterns.prefix("미"), TOP_10);

        assertThat(found).extracting(CountryEntity::getNameKo)
                .containsExactly("미국", "도미니카 공화국");
    }

    @Test
    void 국가_초성_검색은_앞부분_일치로_동작한다() {
        var found = countryRepository.searchByChosung(LikePatterns.prefix("ㄷ"), TOP_10);

        // ㄷ: 독일(ㄷㅇ)·덴마크(ㄷㅁㅋ)·대한민국(ㄷㅎㅁㄱ)·도미니카 공화국(ㄷㅁㄴㅋㄱㅎㄱ) — 짧은 이름부터.
        assertThat(found).extracting(CountryEntity::getNameKo)
                .containsExactly("독일", "덴마크", "대한민국", "도미니카 공화국");
    }

    @Test
    void 두_글자_초성으로_좁혀진다() {
        var found = countryRepository.searchByChosung(LikePatterns.prefix("ㄷㅎ"), TOP_10);

        assertThat(found).extracting(CountryEntity::getCountryId).containsExactly("KR");
    }

    @Test
    void 초성_검색은_이름_컬럼을_건드리지_않는다() {
        // "미국"의 초성은 ㅁㄱ 이므로 초성 검색 "ㄱ"으로는 걸리지 않아야 한다(앞부분 일치).
        var found = countryRepository.searchByChosung(LikePatterns.prefix("ㄱ"), TOP_10);

        assertThat(found).isEmpty();
    }

    @Test
    void 와일드카드_검색어로_전체가_걸리지_않는다() {
        // 이스케이프가 빠지면 "%" 한 글자로 전체 행이 걸린다. 리터럴로 다루면 일치하는 이름이 없다.
        // (서비스는 검색 키 정규화 단계에서 이미 이런 문자를 지우지만, 쿼리 자체도 안전해야 한다.)
        assertThat(countryRepository.searchByName(
                LikePatterns.contains("%"), LikePatterns.prefix("%"), TOP_10)).isEmpty();
        assertThat(countryRepository.searchByName(
                LikePatterns.contains("_"), LikePatterns.prefix("_"), TOP_10)).isEmpty();
    }

    @Test
    void 결과가_없으면_빈_목록이다() {
        var found = countryRepository.searchByName(
                LikePatterns.contains("없는나라"), LikePatterns.prefix("없는나라"), TOP_10);

        assertThat(found).isEmpty();
    }

    @Test
    void 같은_조건이면_인구가_많은_도시가_먼저_온다() {
        var found = cityRepository.searchByName(
                LikePatterns.contains("시"), LikePatterns.prefix("시"), TOP_10);

        assertThat(found).extracting(CityEntity::getNameKo)
                .containsExactly("서울특별시", "부산광역시", "서귀포시");
    }

    @Test
    void 도시_초성_검색도_인구순이다() {
        var found = cityRepository.searchByChosung(LikePatterns.prefix("ㅅ"), TOP_10);

        assertThat(found).extracting(CityEntity::getNameKo)
                .containsExactly("서울특별시", "서귀포시");
    }

    @Test
    void 한국어_이름이_없는_도시는_원문_표기로_검색된다() {
        var found = cityRepository.searchByName(
                LikePatterns.contains("newyork"), LikePatterns.prefix("newyork"), TOP_10);

        assertThat(found).extracting(CityEntity::getCityId).containsExactly("5");
    }

    @Test
    void limit만큼만_돌려준다() {
        var found = cityRepository.searchByName(
                LikePatterns.contains("시"), LikePatterns.prefix("시"), Pageable.ofSize(2));

        assertThat(found).hasSize(2);
    }

    @Test
    void 도시_검색은_국가로_좁히지_않고_소속_국가_정보를_함께_들고_있다() {
        var found = cityRepository.searchByName(
                LikePatterns.contains("도쿄"), LikePatterns.prefix("도쿄"), TOP_10);

        assertThat(found).singleElement()
                .satisfies(city -> {
                    assertThat(city.getCountryId()).isEqualTo("JP");
                    assertThat(city.getCountryNameKo()).isEqualTo("일본");
                });
    }
}
