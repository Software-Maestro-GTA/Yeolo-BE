package com.soma.yeolo.myrealtrip.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 파트너 응답 봉투 {@code {result:{status}, data:{...}}}. FE 어댑터(Yeolo-FE {@code myrealtrip.ts})가 성공으로
 * 보던 조건을 그대로 옮겼다 — {@code data}가 있고, {@code result.status}가 있으면 200 이어야 한다.
 * 그 외 필드는 공급자 내부 메타데이터라 무시한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerEnvelope<T>(PartnerResult result, T data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PartnerResult(Integer status) {
    }

    /** 파트너가 성공으로 간주할 응답인가. */
    public boolean isSuccess() {
        return data != null && (result == null || result.status() == null || result.status() == 200);
    }
}
