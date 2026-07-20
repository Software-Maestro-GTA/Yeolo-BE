package com.soma.yeolo.tasteprofile.domain;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 성향 생성 방식 (DOM-1 §4). DB 저장값은 명세의 소문자 값을 그대로 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum SourceType {

    SURVEY("survey"),
    BEHAVIOR("behavior"),
    MIXED("mixed");

    private final String value;

    public static SourceType fromValue(String value) {
        return Arrays.stream(values())
                .filter(s -> s.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown source type: " + value));
    }
}
