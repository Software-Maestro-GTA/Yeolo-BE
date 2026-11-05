package com.soma.yeolo.myrealtrip.domain;

import java.math.BigDecimal;

/**
 * 파트너 상품 한 건 (API-MRT-3 숙소 / API-MRT-4 투어·티켓). 유효한 항목만 만들어진다 — 상품명·검증된 원본 URL·
 * 0 이상 유한한 가격이 없는 항목은 어댑터가 제외한다(명세 §상품 및 페이지 필드).
 *
 * @param itemName    상품명
 * @param productUrl  검증된 HTTPS 마이리얼트립 원본 URL. Client 의 목록 식별·중복 제거 키이기도 하다
 * @param salePrice   조회 시점 참고 가격(원화). 최종 요금은 마이리얼트립에서 확인
 * @param imageUrl    선택 이미지. 미제공 시 null
 * @param description 선택 설명(투어·티켓). 미제공 시 null
 * @param starRating  선택 숙박 등급(숙소). 리뷰 평점과 구분. 미제공 시 null
 * @param reviewScore 선택 리뷰 평점. 공급자가 문자열 또는 숫자로 주며 명세가 "현재 Client 타입 유지"라 그대로 넘긴다
 *                    ({@link String} 또는 {@link Number}, 미제공 시 null)
 * @param reviewCount 선택 리뷰 수. 미제공 시 null
 */
public record Product(
        String itemName,
        String productUrl,
        BigDecimal salePrice,
        String imageUrl,
        String description,
        BigDecimal starRating,
        Object reviewScore,
        Integer reviewCount
) {

    public Product {
        if (reviewScore != null && !(reviewScore instanceof String) && !(reviewScore instanceof Number)) {
            throw new IllegalArgumentException("reviewScore must be a String or Number: " + reviewScore.getClass());
        }
    }
}
