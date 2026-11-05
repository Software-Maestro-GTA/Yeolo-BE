package com.soma.yeolo.global.client;

import java.util.OptionalLong;
import org.springframework.http.HttpHeaders;

/**
 * 외부 서비스의 {@code Retry-After} 응답 헤더 해석. 초 단위 숫자(delta-seconds)만 받고 HTTP-date 형식·음수·
 * 없음은 빈 값이다 — 호출자가 "재시도 시점을 모른다"로 처리한다(기본 백오프, 또는 헤더 생략).
 * Nominatim 429 백오프와 마이리얼트립 429 전달이 같이 쓴다.
 */
public final class RetryAfterHeader {

    private RetryAfterHeader() {
    }

    public static OptionalLong seconds(HttpHeaders headers) {
        if (headers == null) {
            return OptionalLong.empty();
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return OptionalLong.empty();
        }
        try {
            long seconds = Long.parseLong(value.strip());
            return seconds >= 0 ? OptionalLong.of(seconds) : OptionalLong.empty();
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }
}
