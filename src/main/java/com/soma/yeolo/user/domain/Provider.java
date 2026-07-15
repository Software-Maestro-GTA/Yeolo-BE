package com.soma.yeolo.user.domain;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * OAuth 제공자. DB 저장값은 DOM-3 명세의 소문자 값을 그대로 사용한다. (예: {@code google})
 */
@Getter
@RequiredArgsConstructor
public enum Provider {

    GOOGLE("google");

    private final String value;

    public static Provider fromValue(String value) {
        return Arrays.stream(values())
                .filter(p -> p.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + value));
    }
}
