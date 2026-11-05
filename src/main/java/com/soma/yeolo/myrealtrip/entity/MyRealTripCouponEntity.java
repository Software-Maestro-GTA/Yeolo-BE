package com.soma.yeolo.myrealtrip.entity;

import com.soma.yeolo.global.entity.BaseTimeEntity;
import com.soma.yeolo.myrealtrip.domain.Coupon;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

/**
 * 마이리얼트립 프로모션 쿠폰 테이블 (API-MRT-7). 명세에 테이블 스펙이 없어 BE 에서 정한 스키마다
 * (docs/ddl/myrealtrip_coupons.sql). 앱이 쓰지 않으므로 행은 운영이 SQL 로 넣고 고친다 — 그래서 생성자가 없고
 * 읽기 매핑({@link #toDomain})만 있다.
 *
 * <p>{@code code} 유니크 제약이 "code 는 중복되지 않는다"(명세 §처리 규칙)의 방어선이다. 기간은 날짜 컬럼이다 —
 * 명세가 시각 없는 날짜 단위 운영을 전제하고, 기준일은 서울 날짜라 UTC 시각으로 저장하면 경계가 9시간 어긋난다.
 */
@Getter
@Entity
@Table(name = "myrealtrip_coupons",
        uniqueConstraints = @UniqueConstraint(name = "uk_myrealtrip_coupons_code", columnNames = "code"))
// 운영 SQL 실수(빈 코드·음수 금액·역전된 기간)를 DB 가 먼저 막는다 — 도메인 불변식(Coupon 생성자)과 같은 규칙이다.
// dev 는 ddl-auto 가 만들고 prod 는 docs/ddl/myrealtrip_coupons.sql 의 같은 제약을 적용한다.
@Check(name = "ck_myrealtrip_coupons_code", constraints = "code <> '' AND code = btrim(code)")
@Check(name = "ck_myrealtrip_coupons_amounts", constraints = "minimum_price >= 0 AND discount >= 0")
@Check(name = "ck_myrealtrip_coupons_period", constraints = "starts_on IS NULL OR ends_on IS NULL OR ends_on >= starts_on")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MyRealTripCouponEntity extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 64)
    private String code;

    /** 최소 구매액(원화 정수). */
    @Column(name = "minimum_price", nullable = false)
    private long minimumPrice;

    /** 할인액(원화 정수). */
    @Column(name = "discount", nullable = false)
    private long discount;

    @Column(name = "active", nullable = false)
    private boolean active;

    /** 적용 시작일(포함). NULL 이면 제한 없음. */
    @Column(name = "starts_on")
    private LocalDate startsOn;

    /** 적용 종료일(포함). NULL 이면 제한 없음. */
    @Column(name = "ends_on")
    private LocalDate endsOn;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** 영속 엔티티 → 순수 도메인 매핑. */
    public Coupon toDomain() {
        return new Coupon(code, minimumPrice, discount, active, startsOn, endsOn);
    }
}
