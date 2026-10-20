package com.soma.yeolo.location.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.location.entity.CityEntity;
import com.soma.yeolo.location.entity.CountryEntity;
import org.junit.jupiter.api.Test;

/** 국가·도시 표시명 언어 — 코드 매핑과 엔티티의 표시명 선택. */
class LocationLanguageTest {

    @Test
    void 지원_코드는_대소문자를_무시하고_고른다() {
        assertThat(LocationLanguage.of("en")).isEqualTo(LocationLanguage.EN);
        assertThat(LocationLanguage.of(" EN ")).isEqualTo(LocationLanguage.EN);
        assertThat(LocationLanguage.of("ko")).isEqualTo(LocationLanguage.KO);
    }

    /** 설정으로 지원 언어를 넓혀도 표시명이 없는 언어는 한국어로 떨어진다 — 영문 이름을 내보내는 것보다 낫다. */
    @Test
    void 표시명이_없는_언어와_빈_값은_한국어다() {
        assertThat(LocationLanguage.of("ja")).isEqualTo(LocationLanguage.KO);
        assertThat(LocationLanguage.of(null)).isEqualTo(LocationLanguage.KO);
        assertThat(LocationLanguage.of("")).isEqualTo(LocationLanguage.KO);
    }

    @Test
    void 엔티티는_응답_언어의_표시명을_고른다() {
        CountryEntity japan = CountryEntity.of("JP", "일본", "Japan");
        CityEntity tokyo = CityEntity.of("1850147", "도쿄", "Tokyo", "JP", "일본", 9_733_276L);

        assertThat(japan.displayName(LocationLanguage.KO)).isEqualTo("일본");
        assertThat(japan.displayName(LocationLanguage.EN)).isEqualTo("Japan");
        assertThat(tokyo.displayName(LocationLanguage.KO)).isEqualTo("도쿄");
        assertThat(tokyo.displayName(LocationLanguage.EN)).isEqualTo("Tokyo");
    }

    /** 영문 열이 비어 있는 행(영문 열 추가 전 적재)은 한국어명으로 보인다 — 빈 문자열을 내보내지 않는다. */
    @Test
    void 영문명이_없으면_한국어명으로_보인다() {
        CityEntity legacy = CityEntity.of("1", "서울", null, "KR", "대한민국", 1L);
        CountryEntity blank = CountryEntity.of("KR", "대한민국", " ");

        assertThat(legacy.displayName(LocationLanguage.EN)).isEqualTo("서울");
        assertThat(legacy.getNameEn()).isNull();
        assertThat(legacy.getSearchNameEn()).isNull();
        assertThat(blank.getSearchNameEn()).isNull();
        assertThat(blank.displayName(LocationLanguage.EN)).isEqualTo("대한민국");
    }
}
