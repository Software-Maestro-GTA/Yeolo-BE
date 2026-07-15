package com.soma.yeolo.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 명세의 Error Code/HTTP status를 코드로 표현. 메시지는 명세 문구를 그대로 사용한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Auth / Google OAuth (API-FB-1)
    INVALID_GOOGLE_CODE(HttpStatus.BAD_REQUEST, "인가 코드가 유효하지 않습니다."),
    GOOGLE_AUTH_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Google 인증 처리 중 오류가 발생했습니다."),

    // Common
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 처리 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청이 유효하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
