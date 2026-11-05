package com.soma.yeolo.myrealtrip.dto;

import com.soma.yeolo.myrealtrip.domain.Coupon;
import java.util.List;

/** API-MRT-7 응답 {@code data}. 현재 화면에 필요한 {@code code, minimumPrice, discount}만 내린다(명세 §처리 규칙). */
public record CouponListResponse(List<Entry> coupons) {

    public static CouponListResponse from(List<Coupon> coupons) {
        return new CouponListResponse(coupons.stream()
                .map(c -> new Entry(c.code(), c.minimumPrice(), c.discount()))
                .toList());
    }

    public record Entry(String code, long minimumPrice, long discount) {
    }
}
