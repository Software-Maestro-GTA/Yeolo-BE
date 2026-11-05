package com.soma.yeolo.myrealtrip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.soma.yeolo.myrealtrip.domain.Airport;
import java.util.List;

/**
 * API-MRT-1 응답 {@code data}. 기존 FE 타입 {@code BookingAirport}의 {@code airport} 중첩 구조를 유지한다(명세 §처리 규칙).
 * {@code koName}·{@code enName}은 공급자 미제공 시 생략하며 Client 는 {@code code}를 표시한다.
 */
public record AirportSearchResponse(List<Entry> airports) {

    public static AirportSearchResponse from(List<Airport> airports) {
        return new AirportSearchResponse(airports.stream()
                .map(a -> new Entry(new AirportInfo(a.code(), a.koName(), a.enName())))
                .toList());
    }

    public record Entry(AirportInfo airport) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AirportInfo(String code, String koName, String enName) {
    }
}
