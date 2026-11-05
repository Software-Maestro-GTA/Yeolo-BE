package com.soma.yeolo.myrealtrip.service;

import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.invalid;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.optionalInt;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireDate;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireInt;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireKeyword;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireLong;
import static com.soma.yeolo.myrealtrip.service.MyRealTripRequests.requireUser;

import com.soma.yeolo.myrealtrip.client.MyRealTripPartnerClient;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.dto.HotelSearchResponse;
import com.soma.yeolo.myrealtrip.dto.ProductPageResponse;
import com.soma.yeolo.myrealtrip.dto.TicketSearchResponse;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 마이리얼트립 상품 목록 (API-MRT-3 숙소 조회 / API-MRT-4 투어·티켓 검색).
 *
 * <p>검증 → 사용자별 요청 제한(자동완성과 같은 한도를 공유) → 파트너 호출 순서는 {@link MyRealTripSearchService}와 같다.
 * 과거 날짜 판정 기준일은 명세대로 <b>Asia/Seoul</b> 이다 — 서버 시간대(UTC)로 재면 한국 자정 이후 9시간 동안
 * 어제 날짜가 통과한다. 객실·예약 상세·결제, 이용일·인원별 예약 가능 여부는 이관 범위 밖이다.
 */
@Service
public class MyRealTripProductService {

    static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final int FIRST_PAGE = 1;

    private final MyRealTripPartnerClient partnerClient;
    private final MyRealTripRateLimiter rateLimiter;
    private final Clock clock;

    @Autowired
    public MyRealTripProductService(MyRealTripPartnerClient partnerClient, MyRealTripRateLimiter rateLimiter) {
        this(partnerClient, rateLimiter, Clock.systemUTC());
    }

    /** @param clock 시각만 쓴다 — 기준일의 시간대는 호출 시점에 {@link #SERVICE_ZONE}으로 고정하므로 시계의 존은 무관하다. */
    MyRealTripProductService(MyRealTripPartnerClient partnerClient, MyRealTripRateLimiter rateLimiter, Clock clock) {
        this.partnerClient = partnerClient;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

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
        if (in.isBefore(LocalDate.now(clock.withZone(SERVICE_ZONE))) || !out.isAfter(in)) {
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
