package com.soma.yeolo.myrealtrip.domain;

/**
 * 파트너 숙박 지역 (API-MRT-2). {@code regionId}는 공급자 정수 식별자 그대로이며 BE 국가·도시(API-LOC-*)의
 * 식별자와 혼용하지 않는다(명세 §처리 규칙).
 *
 * @param regionId 공급자 지역 식별자
 * @param name     지역명
 * @param subName  상위 지역·국가 등 보조 이름. 선택
 */
public record Region(long regionId, String name, String subName) {
}
