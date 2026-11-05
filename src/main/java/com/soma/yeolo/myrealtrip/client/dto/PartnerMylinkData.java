package com.soma.yeolo.myrealtrip.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code POST /v1/mylink} 의 {@code data}. 제휴 추적용 단축 링크 하나를 준다. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerMylinkData(String mylink) {
}
