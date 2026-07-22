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

    // Auth / JWT (보호 리소스 공통)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요하거나 토큰이 만료되었습니다."),

    // Taste Profile - Behavior 분석 (API-FB-2 / API-BA-6)
    INSUFFICIENT_IMAGE_METADATA(HttpStatus.BAD_REQUEST, "분석 가능한 이미지 메타데이터가 부족합니다."),
    REVERSE_GEOCODE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "위치 정보 전처리 중 오류가 발생했습니다."),
    AI_ANALYSIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "성향 분석 처리 중 오류가 발생했습니다."),

    // Taste Profile - 조회 (API-FB-8)
    TASTE_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 성향 프로필이 없습니다."),

    // Course - 생성 (API-FB-4 / API-BA-1)
    INVALID_COURSE_CONDITION(HttpStatus.BAD_REQUEST, "여행 조건 입력값이 올바르지 않습니다."),
    AI_COURSE_GENERATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "코스 생성 처리 중 오류가 발생했습니다."),

    // Course - 조회 (API-FB-7 / API-FB-10)
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다."),
    COURSE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "코스에 접근할 권한이 없습니다."),

    // Common
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 처리 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청이 유효하지 않습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
