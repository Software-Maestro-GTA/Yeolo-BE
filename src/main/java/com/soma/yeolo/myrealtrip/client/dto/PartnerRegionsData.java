package com.soma.yeolo.myrealtrip.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** {@code POST /v1/products/accommodation/region-autocomplete} 의 {@code data}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerRegionsData(List<Entry> regions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(Long regionId, String name, String subName) {
    }
}
