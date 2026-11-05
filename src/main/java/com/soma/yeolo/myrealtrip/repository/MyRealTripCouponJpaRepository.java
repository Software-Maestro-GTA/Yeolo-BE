package com.soma.yeolo.myrealtrip.repository;

import com.soma.yeolo.myrealtrip.entity.MyRealTripCouponEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 쿠폰 Spring Data JPA 리포지토리. 포트 구현체 {@link CouponRepositoryImpl} 내부에서만 사용한다.
 * (docs/architecture.md §1-2)
 */
public interface MyRealTripCouponJpaRepository extends JpaRepository<MyRealTripCouponEntity, UUID> {

    /** 활성만, 표시 순서(같으면 코드 — 운영이 순서를 같게 넣어도 응답 순서가 흔들리지 않게). 지난 캠페인 행은 싣지 않는다. */
    List<MyRealTripCouponEntity> findAllByActiveTrueOrderByDisplayOrderAscCodeAsc();
}
