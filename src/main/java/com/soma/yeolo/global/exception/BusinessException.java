package com.soma.yeolo.global.exception;

import lombok.Getter;

/**
 * 명세 기반 비즈니스 예외. {@link ErrorCode}를 담아 전역 핸들러가 응답으로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
}
