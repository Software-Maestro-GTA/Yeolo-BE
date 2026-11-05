package com.soma.yeolo.myrealtrip.client;

import com.soma.yeolo.myrealtrip.domain.Airport;
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
}
