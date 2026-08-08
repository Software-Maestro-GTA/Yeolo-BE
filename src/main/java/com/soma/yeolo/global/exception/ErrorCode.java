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

    // Auth / Google OAuth (API-AUTH-1)
    INVALID_GOOGLE_CODE(HttpStatus.BAD_REQUEST, "인가 코드가 유효하지 않습니다."),
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Google 인증에 실패했습니다."),
    GOOGLE_AUTH_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Google 인증 처리 중 오류가 발생했습니다."),

    // Auth / Apple OAuth (API-AUTH-2)
    INVALID_APPLE_CODE(HttpStatus.BAD_REQUEST, "인가 코드가 유효하지 않습니다."),
    APPLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "Apple 인증에 실패했습니다."),
    APPLE_AUTH_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Apple 인증 처리 중 오류가 발생했습니다."),

    // Auth / 토큰 재발급 (API-AUTH-3)
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh Token이 유효하지 않거나 만료되었습니다."),

    // Auth / JWT (보호 리소스 공통)
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요하거나 토큰이 만료되었습니다."),

    // 사용자 선호 - MBTI (API-PREF-1 / FUN-8)
    INVALID_MBTI(HttpStatus.BAD_REQUEST, "MBTI 입력값을 확인해주세요."),

    // 사용자 프로필 (API-USER-1 / DOM-1)
    INVALID_USER_PROFILE(HttpStatus.BAD_REQUEST, "사용자 프로필 입력값을 확인해주세요."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_IN_USE(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "프로필 이미지 용량이 너무 큽니다."),
    UNSUPPORTED_PROFILE_IMAGE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다."),
    PROFILE_IMAGE_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "프로필 이미지 저장에 실패했습니다."),
    // 회원탈퇴(API-USER-2)의 개인정보 파기 실패. 명세의 500(서버 오류)으로 노출한다.
    PROFILE_IMAGE_DELETE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "프로필 이미지 파기에 실패했습니다."),

    // 사진 데이터 분석 동의 (API-PREF-2 / FUN-3 / REQ-8)
    PHOTO_CONSENT_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "사진 데이터 분석 동의 저장에 실패했습니다."),
    PHOTO_CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "개인정보 수집·활용 동의가 필요합니다."),

    // Taste Profile - Behavior 분석 (API-FB-2 / API-BA-6)
    INSUFFICIENT_IMAGE_METADATA(HttpStatus.BAD_REQUEST, "분석 가능한 이미지 메타데이터가 부족합니다."),
    REVERSE_GEOCODE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "위치 정보 전처리 중 오류가 발생했습니다."),
    AI_ANALYSIS_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "성향 분석 처리 중 오류가 발생했습니다."),

    // Taste Profile - 조회 (API-FB-8)
    TASTE_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "저장된 성향 프로필이 없습니다."),

    // Course - 생성 (API-COURSE-1 / API-AI-2)
    INVALID_COURSE_CONDITION(HttpStatus.BAD_REQUEST, "여행 조건 입력값이 올바르지 않습니다."),
    // DOM-3: MBTI 또는 취향 분석 결과 중 하나 이상이 있으면 코스를 생성할 수 있다 — 둘 다 없을 때만 404.
    USER_PREFERENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "MBTI 또는 성향 정보가 없습니다."),
    AI_COURSE_GENERATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "코스 생성 처리 중 오류가 발생했습니다."),

    // Course - 조회 (API-COURSE-2 / API-COURSE-3)
    COURSE_NOT_FOUND(HttpStatus.NOT_FOUND, "여행 코스를 찾을 수 없습니다."),
    COURSE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "코스에 접근할 권한이 없습니다."),

    // Course - 삭제 (API-COURSE-4). 조회와 메시지가 달라 별도 코드로 둔다(명세 문구 그대로).
    COURSE_DELETE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 여행 코스를 삭제할 권한이 없습니다."),

    // Place - 조회 (API-PLACE-1). 잘못된 placeId(400)는 전역 핸들러가 바인딩 실패에서 만든다.
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "장소 정보를 찾을 수 없습니다."),

    // Location - 국가·도시 자동완성 (API-LOC-1 / API-LOC-2)
    INVALID_COUNTRY_KEYWORD(HttpStatus.BAD_REQUEST, "국가 검색어를 확인해주세요."),
    INVALID_CITY_KEYWORD(HttpStatus.BAD_REQUEST, "도시 검색어를 확인해주세요."),

    // Common
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "내부 처리 오류가 발생했습니다."),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청이 유효하지 않습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
