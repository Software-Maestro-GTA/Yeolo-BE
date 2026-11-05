package com.soma.yeolo.myrealtrip.service;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.myrealtrip.client.MyRealTripPartnerClient;
import com.soma.yeolo.myrealtrip.dto.AirportSearchResponse;
import com.soma.yeolo.myrealtrip.dto.RegionSearchResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 마이리얼트립 자동완성 검색 (API-MRT-1 공항 / API-MRT-2 숙박 지역).
 *
 * <p>순서는 입력 검증 → 사용자별 요청 제한 → 파트너 호출이다. 잘못된 입력은 한도를 소모하지 않고, 한도를
 * 넘은 요청은 파트너에 닿지 않는다(한도의 목적이 파트너 쿼터 보호다). 결과가 없는 것은 200 과 빈 목록이다.
 */
@Service
@RequiredArgsConstructor
public class MyRealTripSearchService {

    private final MyRealTripPartnerClient partnerClient;
    private final MyRealTripRateLimiter rateLimiter;

    public AirportSearchResponse searchAirports(UUID userId, String keyword) {
        String normalized = requireKeyword(keyword);
        rateLimiter.acquire(requireUser(userId));
        return AirportSearchResponse.from(partnerClient.searchAirports(normalized));
    }

    /**
     * @param isDomestic 쿼리 문자열 그대로. 명세가 "true 또는 false 로 전달"이라 그 둘만 받고(대소문자 무시)
     *                   누락·그 외 값은 400 이다. 스프링의 Boolean 변환은 {@code 1}/{@code on}도 받아 쓰지 않았다.
     */
    public RegionSearchResponse searchRegions(UUID userId, String keyword, String isDomestic) {
        String normalized = requireKeyword(keyword);
        boolean domestic = parseIsDomestic(isDomestic);
        rateLimiter.acquire(requireUser(userId));
        return RegionSearchResponse.from(partnerClient.searchRegions(normalized, domestic));
    }

    /**
     * 보호 경로라 보안 필터가 UUID 주체를 채우지만, 주체가 없는 채로 들어오면(설정 변경·다른 인증 방식) 한도 맵에
     * null 키를 넣어 500 이 되는 대신 명세의 401 로 낸다.
     */
    private static UUID requireUser(UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 앞뒤 공백 제거 후 비어 있으면 400 (명세 §요청). */
    private static String requireKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MYREALTRIP_REQUEST);
        }
        return keyword.strip();
    }

    private static boolean parseIsDomestic(String value) {
        if (value == null) {
            throw new BusinessException(ErrorCode.INVALID_MYREALTRIP_REQUEST);
        }
        String v = value.strip();
        if (v.equalsIgnoreCase("true")) {
            return true;
        }
        if (v.equalsIgnoreCase("false")) {
            return false;
        }
        throw new BusinessException(ErrorCode.INVALID_MYREALTRIP_REQUEST);
    }
}
