package com.soma.yeolo.global.exception;

import lombok.Getter;

/**
 * 요청 한도 초과(429). 재시도 가능 시점을 알면 초 단위로 담아 전역 핸들러가 {@code Retry-After}로 내린다.
 * BE 자체 한도(사용자별)와 파트너가 돌려준 429 모두 이 예외로 통일한다(API-MRT-1~7 §실패 응답).
 */
@Getter
public class RateLimitExceededException extends BusinessException {

    /** 재시도까지 남은 초. 모르면 null 이며 그때는 헤더를 내리지 않는다. */
    private final Long retryAfterSeconds;

    public RateLimitExceededException(ErrorCode errorCode, Long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitExceededException(ErrorCode errorCode, Long retryAfterSeconds, Throwable cause) {
        super(errorCode, cause);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
