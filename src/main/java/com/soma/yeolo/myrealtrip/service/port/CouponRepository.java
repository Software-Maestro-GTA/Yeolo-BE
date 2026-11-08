package com.soma.yeolo.myrealtrip.service.port;

import com.soma.yeolo.myrealtrip.domain.Coupon;
import java.util.List;

/**
 * 쿠폰 영속 포트 (API-MRT-7). 서비스가 소유하는 아웃바운드 인터페이스로 순수 도메인만 주고받으며, JPA 세부는 어댑터
 * {@code CouponRepositoryImpl}이 담당한다(docs/architecture.md §1-2). 등록·수정 API 는 명세에 없어 읽기만 둔다 —
 * 쿠폰 변경은 운영이 SQL 로 한다(docs/ddl/myrealtrip_coupons.sql).
 */
public interface CouponRepository {

    /**
     * 활성 쿠폰을 표시 순서(같으면 코드) 오름차순으로. {@code active}는 정적 컬럼이라 저장소가 거르고, 기간 필터는 요청
     * 시점의 서울 날짜가 기준이라 서비스가 도메인 규칙으로 건다. 불변식을 깨는 행(운영 SQL 실수)은 건너뛰고 나머지를
     * 돌려준다. 저장소 접근 실패는 {@code MYREALTRIP_COUPON_LOAD_FAILED}(500).
     */
    List<Coupon> findActiveOrderByDisplayOrder();
}
