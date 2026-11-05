package com.soma.yeolo.myrealtrip.domain;

import java.time.LocalDate;

/**
 * 왕복 항공 검색 조건 (API-MRT-5). 생성자가 불변식을 강제한다 — 대문자 영문 3자리 공항 코드({@link Airport#isValidCode},
 * 공항 검색이 남기는 코드와 같은 규칙), 출발·도착 공항이 다름,
 * 귀국일은 출국일과 같거나 이후, 성인 1명 이상. 사용자 입력의 400 판정은 서비스가 먼저 하고, 여기서는 잘못 조립된 호출이
 * 파트너까지 가지 않게 막는다. 현재 왕복·성인만 지원하며 편도·다구간·소아·유아는 별도 요구사항이다(명세 §처리 규칙).
 *
 * @param depAirportCd 출발 공항 코드(공항 검색 API-MRT-1 이 준 값)
 * @param arrAirportCd 도착 공항 코드
 * @param depDate      출국일
 * @param arrDate      귀국일
 * @param adult        성인 수
 */
public record FlightSearch(String depAirportCd, String arrAirportCd, LocalDate depDate, LocalDate arrDate, int adult) {

    public FlightSearch {
        if (!Airport.isValidCode(depAirportCd) || !Airport.isValidCode(arrAirportCd)) {
            throw new IllegalArgumentException("airport codes must be 3 uppercase letters: "
                    + depAirportCd + " → " + arrAirportCd);
        }
        if (depAirportCd.equals(arrAirportCd)) {
            throw new IllegalArgumentException("departure and arrival airports must differ: " + depAirportCd);
        }
        if (depDate == null || arrDate == null || arrDate.isBefore(depDate)) {
            throw new IllegalArgumentException("arrDate must be on or after depDate: " + depDate + " → " + arrDate);
        }
        if (adult < 1) {
            throw new IllegalArgumentException("adult must be >= 1: " + adult);
        }
    }
}
