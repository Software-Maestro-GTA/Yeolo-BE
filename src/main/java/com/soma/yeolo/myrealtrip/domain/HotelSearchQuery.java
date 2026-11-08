package com.soma.yeolo.myrealtrip.domain;

import java.time.LocalDate;

/**
 * 숙소 조회 조건 (API-MRT-3). 생성자가 불변식을 강제한다 — 체크아웃은 체크인 이후, 성인 1명 이상, 페이지 1 이상.
 * 사용자 입력의 400 판정은 서비스가 먼저 하고, 여기서는 잘못 조립된 호출이 파트너까지 가지 않게 막는다.
 * {@code regionId}는 공급자가 준 정수 그대로라 범위를 제한하지 않는다(API-MRT-2 가 어떤 정수든 넘긴다).
 *
 * @param regionId   숙박 지역 검색(API-MRT-2)에서 고른 공급자 식별자
 * @param checkIn    체크인(시각 없는 날짜)
 * @param checkOut   체크아웃
 * @param adultCount 성인 수
 * @param page       1 부터 시작하는 페이지. 상위 API 는 0 부터라 어댑터가 변환한다
 */
public record HotelSearchQuery(long regionId, LocalDate checkIn, LocalDate checkOut, int adultCount, int page) {

    public HotelSearchQuery {
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut must be after checkIn: " + checkIn + " → " + checkOut);
        }
        if (adultCount < 1) {
            throw new IllegalArgumentException("adultCount must be >= 1: " + adultCount);
        }
        if (page < 1) {
            throw new IllegalArgumentException("page must be >= 1: " + page);
        }
    }
}
