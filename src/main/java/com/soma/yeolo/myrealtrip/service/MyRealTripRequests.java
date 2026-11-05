package com.soma.yeolo.myrealtrip.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.myrealtrip.domain.Airport;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 마이리얼트립 API 공통 입력 검증. 쿼리 파라미터는 전부 문자열로 받아 여기서 엄격히 해석한다 — 스프링의 기본 변환은
 * 누락·형식 오류를 명세 봉투가 아닌 자체 응답으로 내고, Boolean 변환은 {@code 1}/{@code on}도 받기 때문이다.
 * 모든 위반은 명세의 400 한 문구({@link ErrorCode#INVALID_MYREALTRIP_REQUEST})다.
 */
final class MyRealTripRequests {

    /**
     * 과거 날짜 판정 기준일의 시간대 (API-MRT-3 체크인, API-MRT-5 출국일 §처리 규칙 "서비스 기준일 Asia/Seoul"). 서버 시간대(UTC)로
     * 재면 한국 자정 이후 9시간 동안 어제 날짜가 통과한다.
     */
    static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /** ASCII 숫자만. {@code Long.parseLong}은 선행 {@code +}·전각 숫자도 받아 "정수만" 계약보다 느슨하다. 자릿수는 long 전체 범위를 덮는다. */
    private static final Pattern INTEGER = Pattern.compile("-?[0-9]{1,19}");

    private MyRealTripRequests() {
    }

    /**
     * 보호 경로라 보안 필터가 UUID 주체를 채우지만, 주체가 없는 채로 들어오면(설정 변경·다른 인증 방식) 한도 맵에
     * null 키를 넣어 500 이 되는 대신 명세의 401 로 낸다.
     */
    static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 앞뒤 공백 제거 후 비어 있으면 400 (명세 §요청). */
    static String requireKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw invalid();
        }
        return keyword.strip();
    }

    /** 명세가 "true 또는 false 로 전달"이라 그 둘만 받고(대소문자 무시) 누락·그 외 값은 400 이다. */
    static boolean requireBoolean(String value) {
        if (value == null) {
            throw invalid();
        }
        String v = value.strip();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        throw invalid();
    }

    /** 필수 정수(ASCII 숫자, 선택적 {@code -}). 소수·문자열·{@code +}·전각 숫자·범위 밖은 400. */
    static long requireLong(String value, long min) {
        if (value == null) {
            throw invalid();
        }
        String stripped = value.strip();
        if (!INTEGER.matcher(stripped).matches()) {
            throw invalid();
        }
        try {
            long parsed = Long.parseLong(stripped);
            if (parsed < min) {
                throw invalid();
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw invalid();   // 19자리지만 long 범위 밖
        }
    }

    static int requireInt(String value, int min) {
        long parsed = requireLong(value, min);
        if (parsed > Integer.MAX_VALUE) {
            throw invalid();
        }
        return (int) parsed;
    }

    /** 선택 정수 — 없으면 기본값, 있으면 {@code min} 이상이어야 한다. */
    static int optionalInt(String value, int min, int defaultValue) {
        return value == null || value.isBlank() ? defaultValue : requireInt(value, min);
    }

    /** 서울 기준 오늘. 시계의 존은 무관하다(시각만 쓴다). */
    static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(SERVICE_ZONE));
    }

    /** {@code YYYY-MM-DD} 형식의 유효한 날짜(2월 30일 같은 값은 400). */
    static LocalDate requireDate(String value) {
        if (value == null) {
            throw invalid();
        }
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    /** 대문자 영문 3자리 공항 코드(공항 검색이 준 값 그대로). 앞뒤 공백은 다른 필드와 같이 지우고, 소문자·다른 길이는 400 — BE 가 대문자로 보정하지 않는다. */
    static String requireAirportCode(String value) {
        if (value == null) {
            throw invalid();
        }
        String stripped = value.strip();
        if (!Airport.isValidCode(stripped)) {
            throw invalid();
        }
        return stripped;
    }

    static BusinessException invalid() {
        return new BusinessException(ErrorCode.INVALID_MYREALTRIP_REQUEST);
    }
}
