package com.soma.yeolo.myrealtrip.client;

import com.soma.yeolo.myrealtrip.domain.Airport;
import com.soma.yeolo.myrealtrip.domain.FlightSearch;
import com.soma.yeolo.myrealtrip.domain.HotelSearchQuery;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import com.soma.yeolo.myrealtrip.domain.Region;
import java.util.List;

/**
 * 마이리얼트립 파트너 API 포트. 서비스는 이 인터페이스에만 의존하고 HTTP 세부는 어댑터가 맡는다
 * (docs/architecture.md 1-2 외부 호출 포트).
 *
 * <p>구현은 실패를 명세의 상태로 변환해 던진다 — 키 미설정 500, 파트너 인증/IP 거절·호출 실패·잘못된 응답 502,
 * 응답 시간 초과 504, 파트너 요청 제한 429(API-MRT-1~6 §실패 응답). 검색 결과가 없는 것은 실패가 아니라 빈 목록이다.
 */
public interface MyRealTripPartnerClient {

    /** 공항 자동완성 (API-MRT-1). 공급자가 준 대문자 영문 3자리 코드만 남긴다. */
    List<Airport> searchAirports(String keyword);

    /** 숙박 지역 자동완성 (API-MRT-2). 정수 {@code regionId}와 이름이 있는 항목만 남긴다. */
    List<Region> searchRegions(String keyword, boolean isDomestic);

    /** 숙소 조회 (API-MRT-3). 페이지 크기 20 고정, 유효하지 않은 상품은 제외. 응답 구조가 잘못되면 502. */
    ProductPage searchHotels(HotelSearchQuery query);

    /** 투어·티켓 검색 (API-MRT-4). {@code page}는 1 부터. 페이지 크기 20 고정. */
    ProductPage searchTickets(String keyword, int page);

    /**
     * 왕복 항공 검색 랜딩 URL 을 만들고 제휴 링크로 바꾼다 (API-MRT-5). 공급자가 조건·노선을 지원하지 않으면
     * {@code UNSUPPORTED_FLIGHT_SEARCH}(400). 검증된 {@code https://myrealt.rip/...} 만 돌려주며 그 밖은 502.
     */
    String createFlightLink(FlightSearch search);

    /**
     * 상품 원본 URL 을 제휴 추적 링크로 바꾼다 (API-MRT-6). {@code targetUrl}은 호출자가 검증한 마이리얼트립 URL 이어야
     * 하며 고정된 mylink API 의 본문 값으로만 쓴다(임의 HTTP 요청 대상이 아니다). 결과가 {@code https://myrealt.rip/...}가
     * 아니면 502.
     */
    String createProductLink(String targetUrl);
}
