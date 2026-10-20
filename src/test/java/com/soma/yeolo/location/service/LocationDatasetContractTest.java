package com.soma.yeolo.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.location.domain.SearchKeys;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 저장소에 커밋된 데이터셋 파일이 지키는 <b>계약</b> (docs/location-dataset.md §선정 규칙·§한국어 표기 정리).
 *
 * <p>데이터셋은 스크립트 산출물이라 코드 리뷰로는 못 잡는다. 스크립트를 고치거나 출처가 바뀌어 영문 표기가
 * 한글 목록에 다시 섞이거나, 행정 접미사가 되살아나거나, 주요 여행지가 빠지면 여기서 드러난다. DB 없이
 * 파일만 읽으므로 빠르다.
 */
class LocationDatasetContractTest {

    private static final int COUNTRY_ID = 0;
    private static final int NAME_KO = 1;
    private static final int NAME_EN = 2;
    private static final int CITY_COUNTRY_ID = 3;

    private static List<String[]> rows(String location, int columns, int required) {
        try (InputStream in = new ClassPathResource(location).getInputStream()) {
            return LocationSeedFile.parse(in, columns, required).rows();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean hasHangul(String text) {
        return text.chars().anyMatch(ch -> ch >= '가' && ch <= '힣');
    }

    /** 로마자 단어(두 글자 이상 연속). "워싱턴 D.C."·"호세 C. 파즈" 같은 이니셜은 한글 표기의 일부라 허용한다. */
    private static final Pattern LATIN_WORD = Pattern.compile("[A-Za-z]{2,}");

    private static boolean hasLatinWord(String text) {
        return LATIN_WORD.matcher(text).find();
    }

    private final List<String[]> countries = rows("data/locations/countries.tsv",
            LocationSeedLoader.COUNTRY_COLUMNS, LocationSeedLoader.COUNTRY_REQUIRED_COLUMNS);
    private final List<String[]> cities = rows("data/locations/cities.tsv",
            LocationSeedLoader.CITY_COLUMNS, LocationSeedLoader.CITY_COLUMNS);

    /** 영문 표기가 한글 목록에 섞여 나가던 문제의 재발 방지 — 모든 행의 한국어명에 한글이 있고 로마자 단어가 없다. */
    @Test
    void 모든_국가와_도시의_한국어명은_한글이다() {
        assertThat(countries).allSatisfy(row -> assertThat(hasHangul(row[NAME_KO])).as(row[NAME_KO]).isTrue());
        assertThat(cities).allSatisfy(row -> {
            assertThat(hasHangul(row[NAME_KO])).as(row[NAME_KO]).isTrue();
            assertThat(hasLatinWord(row[NAME_KO])).as("로마자 섞임: " + row[NAME_KO]).isFalse();
        });
    }

    @Test
    void 모든_국가와_도시에_영문명이_있다() {
        assertThat(countries).allSatisfy(row -> assertThat(row[NAME_EN]).as(row[COUNTRY_ID]).isNotBlank());
        assertThat(cities).allSatisfy(row -> assertThat(row[NAME_EN]).as(row[NAME_KO]).isNotBlank());
    }

    /** 한국어명은 행정 접미사를 뗀 통용 명칭이다("서울특별시"→"서울"). FE 표시·AI 입력이 이 표기를 쓴다. */
    @Test
    void 한국어_도시명은_행정_접미사를_떼고_영문명은_로마자_접미사를_뗀다() {
        Map<String, String[]> byId = cities.stream().collect(Collectors.toMap(row -> row[0], Function.identity()));
        assertThat(byId.get("1835848")[NAME_KO]).isEqualTo("서울");
        assertThat(byId.get("1838524")[NAME_KO]).isEqualTo("부산");
        assertThat(byId.get("1795565")[NAME_KO]).isEqualTo("선전");
        assertThat(byId.get("1842485")[NAME_EN]).isEqualTo("Goyang");
        // 띄어쓴 " 시"는 국가명과 겹쳐 일부러 남긴 宜蘭(이란 시) 하나뿐이어야 한다(아래 테스트).
        assertThat(cities.stream().filter(row -> row[NAME_KO].endsWith(" 시")).map(row -> row[0]))
                .containsExactly("1674199");
        assertThat(cities).noneSatisfy(row -> assertThat(row[NAME_KO]).endsWith("광역시"));
        assertThat(cities).noneSatisfy(row -> assertThat(row[NAME_EN]).endsWith("-si"));
        // 한국의 군(郡)도 뗀다("홍천군"→"홍천"). 한국 도시의 영문명은 로마자 표기법(ASCII)이어야 한다 —
        // GeoNames 의 매큔-라이샤워 표기("Wŏnju"·"Koch'ang")는 영어 사용자·AI 가 쓰는 표기가 아니다.
        List<String[]> korean = cities.stream().filter(row -> row[CITY_COUNTRY_ID].equals("KR")).toList();
        assertThat(korean).noneSatisfy(row -> assertThat(row[NAME_KO]).endsWith("군"));
        assertThat(korean).allSatisfy(row -> assertThat(row[NAME_EN]).as(row[NAME_KO]).matches("[ -~]+"));
        assertThat(byId.get("1833105")[NAME_EN]).isEqualTo("Wonju");
        assertThat(byId.get("1845020")[NAME_KO]).isEqualTo("통영");
        // 영문명의 붙여 쓴 "shi"·" Province"·동네 이름(GeoNames 영어 별명 오류)도 바로잡혀 있다.
        assertThat(byId.get("1795196")[NAME_EN]).isEqualTo("Tongchuan");
        assertThat(byId.get("2317397")[NAME_EN]).isEqualTo("Bandundu");
        assertThat(byId.get("1848445")[NAME_EN]).isEqualTo("Yatsushiro");
        assertThat(cities).noneSatisfy(row -> assertThat(row[NAME_EN]).endsWith(" Province"));
    }

    /** 도시명이 국가명과 같으면 검색(국가명 완전 일치 우선)으로 닿을 수 없다 — 접미사를 떼다 그렇게 되면 떼지 않는다. */
    @Test
    void 접미사를_뗀_도시명이_국가명과_같아지지_않는다() {
        Map<String, String[]> byId = cities.stream().collect(Collectors.toMap(row -> row[0], Function.identity()));
        assertThat(byId.get("1674199")[NAME_KO]).isEqualTo("이란 시");   // 宜蘭(대만) vs 이란(국가)
    }

    /** 도시의 일부(GeoNames PPLX — 푸둥·코파카바나·시흥동)는 여행 목적지 단위가 아니라 데이터셋에 없다. */
    @Test
    void 도시의_일부인_구역은_들어_있지_않다() {
        List<String> ko = cities.stream().map(row -> row[NAME_KO]).toList();
        assertThat(ko).doesNotContain("푸둥", "코파카바나", "시흥동", "두바이 마리나");
    }

    /** 인구 하한·한국어명 조건을 통과해야 하는 대표 여행지와, 덮어쓰기로 살린 소도시·수도가 들어 있다. */
    @Test
    void 주요_여행지가_들어_있다() {
        Map<String, List<String>> koByCountry = cities.stream().collect(Collectors.groupingBy(
                row -> row[CITY_COUNTRY_ID], Collectors.mapping(row -> row[NAME_KO], Collectors.toList())));
        assertThat(koByCountry.get("JP")).contains("도쿄", "오사카", "교토", "후쿠오카", "삿포로", "사이타마", "이시가키");
        assertThat(koByCountry.get("FR")).contains("파리", "니스", "안시");
        assertThat(koByCountry.get("NL")).contains("암스테르담");
        assertThat(koByCountry.get("VN")).contains("하노이", "호치민", "다낭", "하이퐁", "호이안");
        assertThat(koByCountry.get("TH")).contains("방콕", "치앙마이", "끄라비");
        assertThat(koByCountry.get("US")).contains("뉴욕", "로스앤젤레스");
        // 한 나라 안에서 같은 한글 표기는 하나만 남긴다(프랑크푸르트 암마인·오데르).
        assertThat(koByCountry.get("DE")).containsOnlyOnce("프랑크푸르트");
        // 영문 표기가 같은 도시도 하나만(센다이 仙台·川内) — 영어 목록에서도 같은 이름이 둘 보이면 안 된다.
        List<String> japanEn = cities.stream().filter(row -> row[CITY_COUNTRY_ID].equals("JP"))
                .map(row -> row[NAME_EN].toLowerCase()).toList();
        assertThat(japanEn).containsOnlyOnce("sendai");
    }

    @Test
    void 국가명은_두_언어_모두_정규화_후_유일하다() {
        List<String> keys = countries.stream()
                .flatMap(row -> Stream.of(row[NAME_KO], row[NAME_EN]))
                .map(SearchKeys::normalize)
                .toList();
        assertThat(keys).doesNotHaveDuplicates();
    }
}
