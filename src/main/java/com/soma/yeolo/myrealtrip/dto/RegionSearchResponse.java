package com.soma.yeolo.myrealtrip.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.soma.yeolo.myrealtrip.domain.Region;
import java.util.List;

/** API-MRT-2 응답 {@code data}. {@code subName}은 선택 문자열이라 없으면 생략한다. */
public record RegionSearchResponse(List<Entry> regions) {

    public static RegionSearchResponse from(List<Region> regions) {
        return new RegionSearchResponse(regions.stream()
                .map(r -> new Entry(r.regionId(), r.name(), r.subName()))
                .toList());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Entry(long regionId, String name, String subName) {
    }
}
