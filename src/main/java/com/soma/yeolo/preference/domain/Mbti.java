package com.soma.yeolo.preference.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 사용자 MBTI 선호 입력값 (API-PREF-1 / DOM-1 §"다른 도메인과의 관계").
 *
 * <p>MBTI는 취향 분석 결과가 아니라 코스 생성 시 함께 쓰는 <b>별도 사용자 선호 입력값</b>이며,
 * DOM-1이 "사용자 정보 자체의 필드로 저장하지 않는다"고 못박고 있어 {@code User}가 아닌
 * {@link com.soma.yeolo.preference.entity.UserPreference}에 보관한다.
 *
 * <p>16유형만 허용하는 폐집합이므로 enum으로 고정한다 — 자유 문자열로 두면 오타가 그대로 저장돼
 * AI 코스 생성 요청까지 흘러간다. 저장·전송값은 대문자 4글자다.
 */
public enum Mbti {

    ISTJ, ISFJ, INFJ, INTJ,
    ISTP, ISFP, INFP, INTP,
    ESTP, ESFP, ENFP, ENTP,
    ESTJ, ESFJ, ENFJ, ENTJ;

    /**
     * 입력 문자열을 MBTI 유형으로 해석한다. 앞뒤 공백과 대소문자는 허용한다(FE 입력 폼이
     * 소문자를 보내도 같은 값으로 저장되도록). 16유형이 아니면 빈 값 — 호출부가 400으로 거절한다.
     */
    public static Optional<Mbti> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        // Locale.ROOT 고정: 기본 로케일이 tr 이면 "istj".toUpperCase()가 "İSTJ"가 되어 매칭에
        // 실패한다(터키어 i). MBTI 유형 문자에 i가 들어 있으므로 실제로 걸린다.
        String normalized = value.strip().toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst();
    }
}
