package com.soma.yeolo.location.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 국가·도시 표시명의 언어 (API-LOC-1/2/3 §언어 정책, 2026-10).
 *
 * <p>데이터셋이 들고 있는 표시명은 한국어·영어 두 벌이다({@code name_ko}·{@code name_en}). 표시명 언어는
 * <b>이 enum 이 곧 목록</b>이고 {@code language.supported}와는 별개다 — 그 설정은 사용자가 저장하는 언어이자
 * AI 요청의 {@code language}라서 {@code en}을 넣는 순간 AI 가 영어 요청을 받는다. 데이터셋 표시명은 AI 와
 * 무관하게 낼 수 있으므로 묶지 않는다. 새 언어를 더하려면 데이터셋 열과 여기 상수를 함께 늘린다.
 *
 * <p>검색은 언어와 무관하다. 두 언어의 검색 키를 모두 대상으로 하고(명세 "등록된 다국어 이름·별칭을
 * 검색"), 응답에만 이 언어의 표시명을 쓴다.
 */
public enum LocationLanguage {

    KO("ko", "nameKo"),
    EN("en", "nameEn");

    private final String code;
    /** 엔티티의 표시명 속성 — 정렬({@code Sort.by(property)})이 이 이름을 쓴다. */
    private final String property;

    LocationLanguage(String code, String property) {
        this.code = code;
        this.property = property;
    }

    /** 표시명을 낼 수 있는 언어 코드(RFC 4647 조회의 후보). 기본 언어가 앞이다. */
    public static List<String> codes() {
        return Arrays.stream(values()).map(LocationLanguage::code).toList();
    }

    /** BCP 47 코드로 고른다. 표시명이 없는 언어·빈 값은 한국어다. */
    public static LocationLanguage of(String code) {
        if (code == null) {
            return KO;
        }
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        for (LocationLanguage language : values()) {
            if (language.code.equals(normalized)) {
                return language;
            }
        }
        return KO;
    }

    /**
     * 이 언어의 표시명을 고른다. 영문명이 비어 있으면(영문 열 추가 전 적재, 재적재 실패) 한국어명이다 —
     * 빈 문자열을 내보내는 것보다 낫다. 국가·도시 엔티티가 같은 규칙을 공유한다.
     */
    public String pick(String nameKo, String nameEn) {
        return this == EN && nameEn != null && !nameEn.isBlank() ? nameEn : nameKo;
    }

    public String code() {
        return code;
    }

    public String property() {
        return property;
    }
}
