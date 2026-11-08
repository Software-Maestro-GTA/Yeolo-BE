package com.soma.yeolo.myrealtrip.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** {@code POST /v1/products/flight/airport-autocomplete} 의 {@code data}. 중첩 {@code airport} 구조는 파트너 형식이다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerAirportsData(List<Entry> airports) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(AirportInfo airport) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AirportInfo(String code, String koName, String enName) {
    }
}
