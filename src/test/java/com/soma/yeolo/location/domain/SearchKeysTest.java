package com.soma.yeolo.location.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 검색 키 규칙(API-LOC-1 / API-LOC-2)을 검증한다. 이 규칙이 바뀌면 저장된 키와 검색어가 어긋나
 * 자동완성이 통째로 안 맞으므로, 기대값을 여기에 못 박아 둔다.
 */
class SearchKeysTest {

    @ParameterizedTest
    @CsvSource({
            "대한민국, 대한민국",
            "뉴 욕, 뉴욕",
            "New York, newyork",
            "Saint-Denis, saintdenis",
            "Washington D.C., washingtondc",
    })
    void 정규화는_소문자로_낮추고_공백과_구분자를_지운다(String raw, String expected) {
        assertThat(SearchKeys.normalize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "León, leon",
            "Lüliang, luliang",
            "Thāne, thane",
            "Al Mawşil al Jadīdah, almawsilaljadidah",
            "Málaga, malaga",
    })
    void 발음_부호는_벗겨져_평범한_알파벳으로_검색된다(String raw, String expected) {
        assertThat(SearchKeys.normalize(raw)).isEqualTo(expected);
    }

    @Test
    void 분해형_한글도_결합형과_같은_키가_된다() {
        // macOS에서 복사한 문자열은 NFD(분해형)로 온다. 결합형으로 되돌리지 않으면 자모가
        // 낱개 문자로 남아 키가 통째로 달라진다.
        String decomposed = java.text.Normalizer.normalize("서울", java.text.Normalizer.Form.NFD);
        assertThat(decomposed).isNotEqualTo("서울");

        assertThat(SearchKeys.normalize(decomposed)).isEqualTo(SearchKeys.normalize("서울"));
        assertThat(SearchKeys.chosung(decomposed)).isEqualTo("ㅅㅇ");
    }

    @Test
    void 정규화_입력이_null이면_빈_문자열이다() {
        assertThat(SearchKeys.normalize(null)).isEmpty();
    }

    @Test
    void 구분자만_있는_입력은_정규화하면_비어_검색어로_쓸_수_없다() {
        assertThat(SearchKeys.normalize("  -.- ")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "제주, ㅈㅈ",
            "대한민국, ㄷㅎㅁㄱ",
            "뉴욕, ㄴㅇ",
            "서울특별시, ㅅㅇㅌㅂㅅ",
    })
    void 한글_음절은_초성으로_분해된다(String raw, String expected) {
        assertThat(SearchKeys.chosung(raw)).isEqualTo(expected);
    }

    @Test
    void 한글이_아닌_문자는_초성_키에서_빠진다() {
        assertThat(SearchKeys.chosung("Tokyo")).isEmpty();
        assertThat(SearchKeys.chosung("제주 Jeju 1")).isEqualTo("ㅈㅈ");
    }

    @Test
    void 이미_초성으로_입력된_자모는_그대로_남는다() {
        assertThat(SearchKeys.chosung("ㄷㅎ")).isEqualTo("ㄷㅎ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ㄷ", "ㅁㄱ", "ㅎㄱ"})
    void 자음만_입력하면_초성_검색으로_본다(String keyword) {
        assertThat(SearchKeys.isChosungOnly(keyword)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"대한", "ㅅ울", "tokyo", "ㅏㅣ"})
    void 음절이나_영문_모음이_섞이면_초성_검색이_아니다(String keyword) {
        assertThat(SearchKeys.isChosungOnly(keyword)).isFalse();
    }

    @Test
    void 빈_검색어는_초성_검색이_아니다() {
        assertThat(SearchKeys.isChosungOnly("")).isFalse();
        assertThat(SearchKeys.isChosungOnly(null)).isFalse();
    }
}
