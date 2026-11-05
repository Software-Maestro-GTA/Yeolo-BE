package com.soma.yeolo.myrealtrip.service;

import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.invalid;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.optionalInt;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireDate;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireInt;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireKeyword;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireLong;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireUser;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.today;

import com.soma.yeolo.myrealtrip.client.MyRealTripPartnerClient;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.dto.HotelSearchResponse;
import com.soma.yeolo.myrealtrip.dto.ProductPageResponse;
import com.soma.yeolo.myrealtrip.dto.TicketSearchResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 마이리얼트립 상품 목록 (API-MRT-3 숙소 조회 / API-MRT-4 투어·티켓 검색).
 *
 * <p>검증 → 사용자별 요청 제한(자동완성과 같은 한도를 공유) → 파트너 호출 순서는 {@link MyRealTripSearchService}와 같다.
 * 과거 날짜 판정 기준일은 명세대로 Asia/Seoul 이다({@link MyRealTripRequests#today}). 객실·예약 상세·결제, 이용일·인원별
 * 예약 가능 여부는 이관 범위 밖이다.
 */
@Service
@RequiredArgsConstructor
public class MyRealTripProductService {

    private static final int FIRST_PAGE = 1;

    private final MyRealTripPartnerClient partnerClient;
    private final MyRealTripRateLimiter rateLimiter;
    /** 시각만 쓴다 — 기준일의 시간대는 {@link MyRealTripRequests#today}가 고정한다({@code ClockConfig}). */
    private final Clock clock;


    /**
     * 숙소 조회. 모든 파라미터는 쿼리 문자열 그대로다 — 과거 체크인·날짜 역전·성인 0명·소수 인원·0 페이지는 400.
     * {@code page}는 선택이며 기본 1 이다. {@code regionId}는 정수이기만 하면 된다 — 숙박 지역 검색(API-MRT-2)이 공급자
     * 식별자를 그대로 넘기므로 BE 가 준 값을 BE 가 거절하는 일이 없어야 한다.
     */
    public ProductPageResponse<HotelSearchResponse.Item> searchHotels(UUID userId, String regionId, String checkIn,
                                                                      String checkOut, String adultCount, String page) {
        long region = requireLong(regionId, Long.MIN_VALUE);
        LocalDate in = requireDate(checkIn);
        LocalDate out = requireDate(checkOut);
        if (in.isBefore(today(clock)) || !out.isAfter(in)) {
            throw invalid();
        }
        int adults = requireInt(adultCount, 1);
        int pageNumber = optionalInt(page, FIRST_PAGE, FIRST_PAGE);
        rateLimiter.acquire(requireUser(userId));
        return HotelSearchResponse.from(
                partnerClient.searchHotels(new HotelSearchQuery(region, in, out, adults, pageNumber)));
    }

    /** 투어·티켓 검색. {@code keyword} 필수, {@code page} 선택(기본 1). */
    public ProductPageResponse<TicketSearchResponse.Item> searchTickets(UUID userId, String keyword, String page) {
        String normalized = requireKeyword(keyword);
        int pageNumber = optionalInt(page, FIRST_PAGE, FIRST_PAGE);
        rateLimiter.acquire(requireUser(userId));
        return TicketSearchResponse.from(partnerClient.searchTickets(normalized, pageNumber));
    }
}
