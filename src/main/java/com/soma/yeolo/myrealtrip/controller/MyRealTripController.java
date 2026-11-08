package com.soma.yeolo.myrealtrip.controller;

import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.myrealtrip.dto.AirportSearchResponse;
import com.soma.yeolo.myrealtrip.dto.CouponListResponse;
import com.soma.yeolo.myrealtrip.dto.FlightLinkRequest;
import com.soma.yeolo.myrealtrip.dto.HotelSearchResponse;
import com.soma.yeolo.myrealtrip.dto.LinkResponse;
import com.soma.yeolo.myrealtrip.dto.ProductLinkRequest;
import com.soma.yeolo.myrealtrip.dto.ProductPageResponse;
import com.soma.yeolo.myrealtrip.dto.RegionSearchResponse;
import com.soma.yeolo.myrealtrip.dto.TicketSearchResponse;
import com.soma.yeolo.myrealtrip.service.MyRealTripCouponService;
import com.soma.yeolo.myrealtrip.service.MyRealTripLinkService;
import com.soma.yeolo.myrealtrip.service.MyRealTripProductService;
import com.soma.yeolo.myrealtrip.service.MyRealTripSearchService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이리얼트립 제휴 연동 API (API-MRT-1 공항 검색 / API-MRT-2 숙박 지역 검색 / API-MRT-3 숙소 조회 /
 * API-MRT-4 투어·티켓 검색 / API-MRT-5 항공 검색 제휴 링크 / API-MRT-6 상품 제휴 링크 / API-MRT-7 쿠폰 목록).
 *
 * <p>FE 가 파트너 키를 들고 직접 호출하던 것을 BE 가 대신 호출한다(2026-11 명세 개정, SPEC {@code a816a75}).
 * 모든 엔드포인트는 로그인 필수이며({@code SecurityConfig}의 {@code anyRequest().authenticated()}) 미인증은 401 봉투다.
 * 쿼리 파라미터는 {@code required=false}로 받고 서비스가 명세의 400 문구로 거절한다 — 스프링의 기본 누락 응답은
 * 명세 봉투가 아니다({@code LocationController}와 같은 이유).
 */
@Slf4j
@RestController
@RequestMapping("/api/myrealtrip")
@RequiredArgsConstructor
public class MyRealTripController {

    /**
     * 이 컨트롤러의 본문 파싱 실패(깨진 JSON, 배열·객체가 온 스칼라 필드)는 명세의 400 문구로 낸다 — 전역 핸들러의
     * "요청이 유효하지 않습니다."가 아니라 API-MRT-5/6 §실패 응답의 "요청 값을 확인해주세요." 다. 컨트롤러 안의
     * {@code @ExceptionHandler}가 {@code @RestControllerAdvice}보다 먼저 잡힌다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        ErrorCode code = ErrorCode.INVALID_MYREALTRIP_REQUEST;
        log.warn("마이리얼트립 요청 본문을 읽을 수 없다: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
    }

    private final MyRealTripSearchService searchService;
    private final MyRealTripProductService productService;
    private final MyRealTripLinkService linkService;
    private final MyRealTripCouponService couponService;

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

    /** 본문 누락은 {@code required=false}로 받아 서비스가, 깨진 JSON 은 {@link #handleUnreadableBody}가 명세의 400 문구로 거절한다. */
    @PostMapping("/flights/link")
    public ApiResponse<LinkResponse> createFlightLink(@AuthenticationPrincipal UUID userId,
                                                      @RequestBody(required = false) FlightLinkRequest request) {
        return ApiResponse.success("항공 검색 링크 생성 성공", linkService.createFlightLink(userId, request));
    }

    /** 파트너 호출 없이 BE 가 관리하는 활성 쿠폰만 돌려준다. 파라미터·본문 없음. */
    @GetMapping("/coupons")
    public ApiResponse<CouponListResponse> listCoupons(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.success("쿠폰 정보 조회 성공", couponService.list(userId));
    }

    @PostMapping("/links")
    public ApiResponse<LinkResponse> createProductLink(@AuthenticationPrincipal UUID userId,
                                                       @RequestBody(required = false) ProductLinkRequest request) {
        return ApiResponse.success("상품 제휴 링크 생성 성공", linkService.createProductLink(userId, request));
    }
}
