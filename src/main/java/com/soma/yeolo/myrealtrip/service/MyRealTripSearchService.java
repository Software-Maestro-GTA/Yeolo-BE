package com.soma.yeolo.myrealtrip.service;

import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireBoolean;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireKeyword;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireUser;

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
 * 입력 해석 규칙은 {@link MyRealTripRequests}에 있다.
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

    /** @param isDomestic 쿼리 문자열 그대로({@code true}/{@code false}). 누락·그 외 값은 400. */
    public RegionSearchResponse searchRegions(UUID userId, String keyword, String isDomestic) {
        String normalized = requireKeyword(keyword);
        boolean domestic = requireBoolean(isDomestic);
        rateLimiter.acquire(requireUser(userId));
        return RegionSearchResponse.from(partnerClient.searchRegions(normalized, domestic));
    }
}
