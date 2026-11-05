package com.soma.yeolo.myrealtrip.domain;

import java.time.LocalDate;

/**
 * 마이리얼트립 투어·티켓 프로모션 쿠폰 (API-MRT-7). 앱 상수({@code MYREALTRIP_TICKET_COUPONS})를 서버 관리 데이터로 옮긴 것으로,
 * 공개 프로모션 코드 목록이지 개인 쿠폰 발급·사용·검증이 아니다. Client 는 코드를 복사해 마이리얼트립 결제 화면에 입력한다.
 *
 * <p>노출 규칙은 {@link #isAvailableOn}: 활성이고 적용 기간 안이어야 한다. 기간은 날짜 단위(시각 없음)이며 양 끝을 포함하고,
 * 비어 있으면 그쪽 경계가 없다. 기준일의 시간대는 다른 마이리얼트립 API 와 같은 Asia/Seoul 이다.
 *
 * @param code         프로모션 코드. 중복 없음(저장소 유니크 제약)
 * @param minimumPrice 최소 구매액(원화 정수)
 * @param discount     할인액(원화 정수)
 * @param active       운영이 켜고 끄는 노출 스위치
 * @param startsOn     적용 시작일(포함). null 이면 제한 없음
 * @param endsOn       적용 종료일(포함). null 이면 제한 없음
 *
 * <p>표시 순서는 도메인 값이 아니다 — 저장소가 {@code display_order, code} 순으로 정렬해 돌려주고 서비스는 그 순서를
 * 바꾸지 않는다(포트 {@code CouponRepository} 계약).
 */
public record Coupon(String code, long minimumPrice, long discount, boolean active,
                     LocalDate startsOn, LocalDate endsOn) {

    public Coupon {
        // 앞뒤 공백(탭·개행 포함)이 붙은 코드는 거절한다 — 정리해서 내보내면 'X\t' 와 'X' 가 UNIQUE 를 함께 통과한 뒤
        // 같은 코드로 두 번 나간다("code 는 중복되지 않는다" 위반). DB CHECK(btrim)는 ASCII 공백만 보므로 나머지는 여기서 막고,
        // 어댑터가 그 행만 제외한다.
        if (code == null || code.isBlank() || !code.equals(code.strip())) {
            throw new IllegalArgumentException("code must not be blank or padded: '" + code + "'");
        }
        if (minimumPrice < 0 || discount < 0) {
            throw new IllegalArgumentException("amounts must be >= 0: minimumPrice=" + minimumPrice + ", discount=" + discount);
        }
        if (startsOn != null && endsOn != null && endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("endsOn must be on or after startsOn: " + startsOn + " → " + endsOn);
        }
    }

    /** 활성이고 {@code today}가 적용 기간(양 끝 포함) 안이면 노출한다. */
    public boolean isAvailableOn(LocalDate today) {
        return active
                && (startsOn == null || !today.isBefore(startsOn))
                && (endsOn == null || !today.isAfter(endsOn));
    }
}
