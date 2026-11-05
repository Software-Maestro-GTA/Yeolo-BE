package com.soma.yeolo.myrealtrip.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.myrealtrip.dto.AirportSearchResponse;
import com.soma.yeolo.myrealtrip.dto.HotelSearchResponse;
import com.soma.yeolo.myrealtrip.dto.ProductPageResponse;
import com.soma.yeolo.myrealtrip.dto.RegionSearchResponse;
import com.soma.yeolo.myrealtrip.dto.TicketSearchResponse;
import com.soma.yeolo.myrealtrip.service.MyRealTripProductService;
import com.soma.yeolo.myrealtrip.service.MyRealTripSearchService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이리얼트립 제휴 연동 API (API-MRT-1 공항 검색 / API-MRT-2 숙박 지역 검색 / API-MRT-3 숙소 조회 /
 * API-MRT-4 투어·티켓 검색).
 *
 * <p>FE 가 파트너 키를 들고 직접 호출하던 것을 BE 가 대신 호출한다(2026-11 명세 개정, SPEC {@code a816a75}).
 * 모든 엔드포인트는 로그인 필수이며({@code SecurityConfig}의 {@code anyRequest().authenticated()}) 미인증은 401 봉투다.
 * 쿼리 파라미터는 {@code required=false}로 받고 서비스가 명세의 400 문구로 거절한다 — 스프링의 기본 누락 응답은
 * 명세 봉투가 아니다({@code LocationController}와 같은 이유).
 */
@RestController
@RequestMapping("/api/myrealtrip")
@RequiredArgsConstructor
public class MyRealTripController {

    private final MyRealTripSearchService searchService;
    private final MyRealTripProductService productService;

    @GetMapping("/airports")
    public ApiResponse<AirportSearchResponse> searchAirports(@AuthenticationPrincipal UUID userId,
                                                             @RequestParam(required = false) String keyword) {
        return ApiResponse.success("공항 검색 성공", searchService.searchAirports(userId, keyword));
    }

    @GetMapping("/regions")
    public ApiResponse<RegionSearchResponse> searchRegions(@AuthenticationPrincipal UUID userId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String isDomestic) {
        return ApiResponse.success("숙박 지역 검색 성공", searchService.searchRegions(userId, keyword, isDomestic));
    }

    @GetMapping("/hotels")
    public ApiResponse<ProductPageResponse<HotelSearchResponse.Item>> searchHotels(@AuthenticationPrincipal UUID userId,
                                                         @RequestParam(required = false) String regionId,
                                                         @RequestParam(required = false) String checkIn,
                                                         @RequestParam(required = false) String checkOut,
                                                         @RequestParam(required = false) String adultCount,
                                                         @RequestParam(required = false) String page) {
        return ApiResponse.success("숙소 조회 성공",
                productService.searchHotels(userId, regionId, checkIn, checkOut, adultCount, page));
    }

    @GetMapping("/tickets")
    public ApiResponse<ProductPageResponse<TicketSearchResponse.Item>> searchTickets(@AuthenticationPrincipal UUID userId,
                                                           @RequestParam(required = false) String keyword,
                                                           @RequestParam(required = false) String page) {
        return ApiResponse.success("투어·티켓 검색 성공", productService.searchTickets(userId, keyword, page));
    }
}
