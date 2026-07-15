package com.soma.yeolo.user.domain;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 계정 상태. DB 저장값은 DOM-3 명세의 소문자 값을 그대로 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum UserStatus {

    ACTIVE("active"),
    INACTIVE("inactive"),
    DELETED("deleted");

    private final String value;

    public static UserStatus fromValue(String value) {
        return Arrays.stream(values())
                .filter(s -> s.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown user status: " + value));
    }
}
