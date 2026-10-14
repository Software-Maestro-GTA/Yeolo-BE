package com.soma.yeolo.location.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.location.domain.LikePatterns;
import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CityImageEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/** 2026-10 개정 후 남은 조회(전체 목록·완전 일치·접두어·사진 폴백)가 실제 SQL 에서 맞게 도는지 확인한다. */
@DataJpaTest
class LocationRepositoryTest {

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private CityImageRepository cityImageRepository;

    @BeforeEach
    void setUp() {
        countryRepository.saveAll(List.of(
                CountryEntity.of("KR", "대한민국", "1", "KRW", "₩"),
                CountryEntity.of("DK", "덴마크"),
                CountryEntity.of("DE", "독일"),
                CountryEntity.of("US", "미국", "7", "USD", "$"),
                CountryEntity.of("DO", "도미니카 공화국"),
                CountryEntity.of("JP", "일본", "4", "JPY", "¥")));
        cityRepository.saveAll(List.of(
                CityEntity.of("1", "서울특별시", "KR", "대한민국", 10_349_312L),
                CityEntity.of("2", "서귀포시", "KR", "대한민국", 155_691L),
                CityEntity.of("3", "부산광역시", "KR", "대한민국", 3_285_147L),
                CityEntity.of("4", "도쿄", "JP", "일본", 9_733_276L),
                CityEntity.of("5", "New York City", "US", "미국", 8_804_190L),
                CityEntity.of("6", "도미니카", "DO", "도미니카 공화국", 1_000L),
                CityEntity.of("7", "워싱턴 D.C.", "US", "미국", 700_000L),
                CityEntity.of("8", "로스앤젤레스", "US", "미국", 3_900_000L)));
        cityImageRepository.saveAll(List.of(
                CityImageEntity.of("4", "locations/cities/4.jpg", "pixabay", "1"),
                CityImageEntity.of("5", "locations/cities/5.jpg", "pixabay", "2"),
                CityImageEntity.of("7", "locations/cities/7.jpg", "pixabay", "3")));
    }

    // ===== 국가 =====

    @Test
    void 국가_전체_목록은_표시명_오름차순이다() {
        assertThat(countryRepository.findAllByOrderByNameKoAsc()).extracting(CountryEntity::getNameKo)
                .containsExactly("대한민국", "덴마크", "도미니카 공화국", "독일", "미국", "일본");
    }

    @Test
    void 국가_완전_일치는_정규화된_키로_찾는다() {
        assertThat(countryRepository.findBySearchName("대한민국")).map(CountryEntity::getCountryId).contains("KR");
        assertThat(countryRepository.findBySearchName("도미니카공화국")).map(CountryEntity::getCountryId).contains("DO");
        assertThat(countryRepository.findBySearchName("대한")).isEmpty();
    }

    @Test
    void 국가_접두어_검색은_표시명_오름차순이고_중간_일치는_걸리지_않는다() {
        // 음절 단위 접두어다: "도"는 도미니카 공화국만이고 독일("독")·대한민국("대")은 아니다.
        // (초성 검색 "ㄷ"은 2026-10 개정으로 명세에서 빠졌다.)
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("도")))
                .extracting(CountryEntity::getNameKo)
                .containsExactly("도미니카 공화국");
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("ㄷ"))).isEmpty();
        // 중간 일치("미")는 미국만 — 도미니카 공화국은 접두어가 아니다.
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("미")))
                .extracting(CountryEntity::getNameKo)
                .containsExactly("미국");
    }

    @Test
    void 수도와_통화가_함께_적재된다() {
        CountryEntity japan = countryRepository.findById("JP").orElseThrow();
        assertThat(japan.getCapitalCityId()).isEqualTo("4");
        assertThat(japan.getCurrencyCode()).isEqualTo("JPY");
        assertThat(japan.getCurrencySymbol()).isEqualTo("¥");
        CountryEntity denmark = countryRepository.findById("DK").orElseThrow();
        assertThat(denmark.getCapitalCityId()).isNull();
        assertThat(denmark.getCurrencyCode()).isNull();
    }

    // ===== 도시 =====

    @Test
    void 국가별_도시_전체_목록은_표시명_오름차순이고_페이지네이션이_없다() {
        assertThat(cityRepository.findByCountryIdOrderByNameKoAsc("KR")).extracting(CityEntity::getNameKo)
                .containsExactly("부산광역시", "서귀포시", "서울특별시");
    }

    @Test
    void 도시_접두어_검색은_전_세계를_대상으로_한다() {
        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("도"))).extracting(CityEntity::getNameKo)
                .containsExactlyInAnyOrder("도쿄", "도미니카");
    }

    @Test
    void 한국어_이름이_없는_도시는_원문_표기를_정규화한_키로_검색된다() {
        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("newyork"))).extracting(CityEntity::getCityId)
                .containsExactly("5");
    }

    @Test
    void 와일드카드_검색어로_전체가_걸리지_않는다() {
        // 이스케이프가 빠지면 "%" 한 글자로 전체 행이 걸린다. 리터럴로 다루면 일치하는 이름이 없다.
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("%"))).isEmpty();
        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("_"))).isEmpty();
    }

    @Test
    void 국가_안_도시_완전_일치는_동명이면_인구가_많은_쪽이다() {
        cityRepository.save(CityEntity.of("9", "서울특별시", "KR", "대한민국", 10L));

        assertThat(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc("KR", "서울특별시"))
                .map(CityEntity::getCityId).contains("1");
        assertThat(cityRepository.findFirstByCountryIdAndSearchNameOrderByPopulationDesc("JP", "서울특별시"))
                .isEmpty();
    }

    @Test
    void 식별자_목록의_국가를_표시명_오름차순으로_돌려준다() {
        assertThat(countryRepository.findByCountryIdInOrderByNameKoAsc(List.of("US", "JP", "KR")))
                .extracting(CountryEntity::getNameKo)
                .containsExactly("대한민국", "미국", "일본");
    }

    @Test
    void 여러_국가의_도시를_국가별로_묶어_표시명_오름차순으로_돌려준다() {
        assertThat(cityRepository.findByCountryIdInOrderByCountryIdAscNameKoAsc(List.of("KR", "JP")))
                .extracting(CityEntity::getNameKo)
                .containsExactly("도쿄", "부산광역시", "서귀포시", "서울특별시");
    }

    @Test
    void 국가_안_도시_접두어_일치는_인구가_많은_쪽이다() {
        assertThat(cityRepository.findFirstByCountryIdAndSearchNameStartingWithOrderByPopulationDesc("KR", "서"))
                .map(CityEntity::getNameKo).contains("서울특별시");
        assertThat(cityRepository.findFirstByCountryIdAndSearchNameStartingWithOrderByPopulationDesc("JP", "서"))
                .isEmpty();
    }

    @Test
    void 사진이_있는_도시를_인구순으로_찾는다() {
        // 미국: 로스앤젤레스(사진 없음) > New York City(사진) > 워싱턴(사진) → 뉴욕이 먼저.
        assertThat(cityRepository.findWithImageByCountryIdOrderByPopulationDesc("US", PageRequest.of(0, 1)))
                .extracting(CityEntity::getCityId).containsExactly("5");
        assertThat(cityRepository.findWithImageByCountryIdOrderByPopulationDesc("KR", PageRequest.of(0, 1)))
                .isEmpty();
    }
}
