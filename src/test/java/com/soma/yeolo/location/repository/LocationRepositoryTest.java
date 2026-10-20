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
import org.springframework.data.domain.Sort;

/** 2026-10 개정 후 남은 조회(전체 목록·완전 일치·접두어·사진 폴백)가 실제 SQL 에서 맞게 도는지 확인한다. */
@DataJpaTest
class LocationRepositoryTest {

    private static final Sort BY_KO = Sort.by("nameKo");
    private static final Sort BY_EN = Sort.by("nameEn");
    private static final Sort BY_COUNTRY_KO = Sort.by("countryId").and(BY_KO);
    private static final PageRequest FIRST = PageRequest.of(0, 1);

    @Autowired
    private CountryRepository countryRepository;

    @Autowired
    private CityRepository cityRepository;

    @Autowired
    private CityImageRepository cityImageRepository;

    @BeforeEach
    void setUp() {
        countryRepository.saveAll(List.of(
                CountryEntity.of("KR", "대한민국", "South Korea", "1", "KRW", "₩"),
                CountryEntity.of("DK", "덴마크", "Denmark"),
                CountryEntity.of("DE", "독일", "Germany"),
                CountryEntity.of("US", "미국", "United States", "7", "USD", "$"),
                CountryEntity.of("DO", "도미니카 공화국", "Dominican Republic"),
                CountryEntity.of("JP", "일본", "Japan", "4", "JPY", "¥")));
        cityRepository.saveAll(List.of(
                CityEntity.of("1", "서울", "Seoul", "KR", "대한민국", 10_349_312L),
                CityEntity.of("2", "서귀포", "Seogwipo", "KR", "대한민국", 155_691L),
                CityEntity.of("3", "부산", "Busan", "KR", "대한민국", 3_285_147L),
                CityEntity.of("4", "도쿄", "Tokyo", "JP", "일본", 9_733_276L),
                CityEntity.of("5", "뉴욕", "New York", "US", "미국", 8_804_190L),
                CityEntity.of("6", "도미니카", "Dominica", "DO", "도미니카 공화국", 1_000L),
                CityEntity.of("7", "워싱턴 D.C.", "Washington, D.C.", "US", "미국", 700_000L),
                CityEntity.of("8", "로스앤젤레스", "Los Angeles", "US", "미국", 3_900_000L)));
        cityImageRepository.saveAll(List.of(
                CityImageEntity.of("4", "locations/cities/4.jpg", "pixabay", "1"),
                CityImageEntity.of("5", "locations/cities/5.jpg", "pixabay", "2"),
                CityImageEntity.of("7", "locations/cities/7.jpg", "pixabay", "3")));
    }

    // ===== 국가 =====

    @Test
    void 국가_전체_목록은_응답_언어의_표시명_오름차순이다() {
        assertThat(countryRepository.findAll(BY_KO)).extracting(CountryEntity::getNameKo)
                .containsExactly("대한민국", "덴마크", "도미니카 공화국", "독일", "미국", "일본");
        assertThat(countryRepository.findAll(BY_EN)).extracting(CountryEntity::getNameEn)
                .containsExactly("Denmark", "Dominican Republic", "Germany", "Japan", "South Korea", "United States");
    }

    @Test
    void 국가_완전_일치는_두_언어의_정규화된_키로_찾는다() {
        assertThat(countryRepository.findByAnySearchName("대한민국")).map(CountryEntity::getCountryId).contains("KR");
        assertThat(countryRepository.findByAnySearchName("southkorea")).map(CountryEntity::getCountryId).contains("KR");
        assertThat(countryRepository.findByAnySearchName("도미니카공화국")).map(CountryEntity::getCountryId).contains("DO");
        assertThat(countryRepository.findByAnySearchName("대한")).isEmpty();
    }

    @Test
    void 국가_접두어_검색은_표시명_오름차순이고_중간_일치는_걸리지_않는다() {
        // 음절 단위 접두어다: "도"는 도미니카 공화국만이고 독일("독")·대한민국("대")은 아니다.
        // (초성 검색 "ㄷ"은 2026-10 개정으로 명세에서 빠졌다.)
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("도"), BY_KO))
                .extracting(CountryEntity::getNameKo)
                .containsExactly("도미니카 공화국");
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("ㄷ"), BY_KO)).isEmpty();
        // 중간 일치("미")는 미국만 — 도미니카 공화국은 접두어가 아니다.
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("미"), BY_KO))
                .extracting(CountryEntity::getNameKo)
                .containsExactly("미국");
    }

    /** 검색 입력 언어와 응답 언어는 별개다(명세) — 영문 접두어로 찾아도 한국어 표시명으로 정렬·응답할 수 있다. */
    @Test
    void 국가_접두어_검색은_영문_키도_본다() {
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("d"), BY_KO))
                .extracting(CountryEntity::getNameKo)
                .containsExactly("덴마크", "도미니카 공화국");
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("d"), BY_EN))
                .extracting(CountryEntity::getNameEn)
                .containsExactly("Denmark", "Dominican Republic");
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
    void 국가별_도시_전체_목록은_응답_언어의_표시명_오름차순이고_페이지네이션이_없다() {
        assertThat(cityRepository.findByCountryId("KR", BY_KO)).extracting(CityEntity::getNameKo)
                .containsExactly("부산", "서귀포", "서울");
        assertThat(cityRepository.findByCountryId("KR", BY_EN)).extracting(CityEntity::getNameEn)
                .containsExactly("Busan", "Seogwipo", "Seoul");
    }

    @Test
    void 도시_접두어_검색은_전_세계를_대상으로_한다() {
        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("도"), BY_COUNTRY_KO)).extracting(CityEntity::getNameKo)
                .containsExactlyInAnyOrder("도쿄", "도미니카");
    }

    @Test
    void 도시_접두어_검색은_영문_키도_본다() {
        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("newyork"), BY_COUNTRY_KO))
                .extracting(CityEntity::getCityId).containsExactly("5");
        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("se"), BY_COUNTRY_KO))
                .extracting(CityEntity::getNameKo).containsExactly("서귀포", "서울");
    }

    @Test
    void 와일드카드_검색어로_전체가_걸리지_않는다() {
        // 이스케이프가 빠지면 "%" 한 글자로 전체 행이 걸린다. 리터럴로 다루면 일치하는 이름이 없다.
        assertThat(countryRepository.searchByPrefix(LikePatterns.prefix("%"), BY_KO)).isEmpty();
        assertThat(cityRepository.searchByPrefix(LikePatterns.prefix("_"), BY_COUNTRY_KO)).isEmpty();
    }

    @Test
    void 국가_안_도시_완전_일치는_두_언어를_보고_동명이면_인구가_많은_쪽이_앞이다() {
        cityRepository.save(CityEntity.of("9", "서울", "Seoul", "KR", "대한민국", 10L));

        assertThat(cityRepository.findByCountryIdAndAnySearchName("KR", "서울", FIRST))
                .extracting(CityEntity::getCityId).containsExactly("1");
        assertThat(cityRepository.findByCountryIdAndAnySearchName("KR", "seoul", FIRST))
                .extracting(CityEntity::getCityId).containsExactly("1");
        assertThat(cityRepository.findByCountryIdAndAnySearchName("JP", "서울", FIRST)).isEmpty();
    }

    @Test
    void 식별자_목록의_국가를_응답_언어의_표시명_오름차순으로_돌려준다() {
        assertThat(countryRepository.findByCountryIdIn(List.of("US", "JP", "KR"), BY_KO))
                .extracting(CountryEntity::getNameKo)
                .containsExactly("대한민국", "미국", "일본");
        assertThat(countryRepository.findByCountryIdIn(List.of("US", "JP", "KR"), BY_EN))
                .extracting(CountryEntity::getNameEn)
                .containsExactly("Japan", "South Korea", "United States");
    }

    @Test
    void 여러_국가의_도시를_국가별로_묶어_표시명_오름차순으로_돌려준다() {
        assertThat(cityRepository.findByCountryIdIn(List.of("KR", "JP"), BY_COUNTRY_KO))
                .extracting(CityEntity::getNameKo)
                .containsExactly("도쿄", "부산", "서귀포", "서울");
    }

    @Test
    void 국가_안_도시_접두어_일치는_두_언어를_보고_인구가_많은_쪽이_앞이다() {
        assertThat(cityRepository.findByCountryIdAndAnySearchNamePrefix("KR", LikePatterns.prefix("서"), FIRST))
                .extracting(CityEntity::getNameKo).containsExactly("서울");
        assertThat(cityRepository.findByCountryIdAndAnySearchNamePrefix("KR", LikePatterns.prefix("seo"), FIRST))
                .extracting(CityEntity::getNameKo).containsExactly("서울");
        assertThat(cityRepository.findByCountryIdAndAnySearchNamePrefix("JP", LikePatterns.prefix("서"), FIRST))
                .isEmpty();
    }

    @Test
    void 사진이_있는_도시를_인구순으로_찾는다() {
        // 미국: 로스앤젤레스(사진 없음) > 뉴욕(사진) > 워싱턴(사진) → 뉴욕이 먼저.
        assertThat(cityRepository.findWithImageByCountryIdOrderByPopulationDesc("US", PageRequest.of(0, 1)))
                .extracting(CityEntity::getCityId).containsExactly("5");
        assertThat(cityRepository.findWithImageByCountryIdOrderByPopulationDesc("KR", PageRequest.of(0, 1)))
                .isEmpty();
    }
}
