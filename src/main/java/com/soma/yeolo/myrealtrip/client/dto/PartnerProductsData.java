package com.soma.yeolo.myrealtrip.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * {@code POST /v1/products/accommodation/search} · {@code POST /v1/products/tna/search} 의 {@code data}.
 *
 * <p>항목은 타입을 정하지 않고 {@link Object}(JSON 객체는 {@code Map}, 스칼라는 String/Number)로 받는다 — 명세가
 * "상품명·원본 URL·가격이 유효하지 않은 항목은 제외"라 한 항목의 타입 오류(예: 문자열 가격)가 페이지 전체를 502 로
 * 만들면 안 되고, 어댑터가 항목별로 검사해 걸러야 한다. 트리 모델({@code JsonNode})을 쓰지 않는 이유는 RestClient 의
 * 변환기가 Jackson 3 인데 프로젝트의 {@code JsonNode}는 Jackson 2 라 역직렬화 대상이 될 수 없어서다.
 * {@code items}가 배열이 아니면 역직렬화가 실패해 502 다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PartnerProductsData(List<Object> items, Object totalCount) {
}
