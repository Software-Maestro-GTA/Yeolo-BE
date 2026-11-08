package com.soma.yeolo.myrealtrip.repository;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.myrealtrip.domain.Coupon;
import com.soma.yeolo.myrealtrip.entity.MyRealTripCouponEntity;
import com.soma.yeolo.myrealtrip.service.port.CouponRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.stereotype.Component;

/**
 * {@link CouponRepository} 포트의 JPA 어댑터. 도메인↔엔티티 매핑과 저장소 예외 번역을 이 경계에 격리한다
 * (docs/architecture.md §1-2).
 *
 * <p>DB 접근 실패는 명세의 500 문구({@code MYREALTRIP_COUPON_LOAD_FAILED})로 바꾼다 — 서비스가 Spring DAO 예외를 알 필요가
 * 없고, catch-all 의 공통 500 문구와 달라야 한다(API-MRT-7 §실패 응답). 로그는 전역 핸들러가 5xx 원인과 함께 남기므로
 * 여기서 또 찍지 않는다. DB 가 내려가 커넥션을 못 얻으면 쿼리 전에 트랜잭션 시작에서 {@link TransactionException}이
 * 나므로 그것도 같이 번역한다. 불변식을 깨는 행(DDL 의 CHECK 가 1차 방어선이지만 운영 SQL 은 사람이 친다)은 그 행만 빼고
 * 행 id 당 한 번만 WARN 을 남긴다 — 한 행 때문에 멀쩡한 쿠폰까지 500 으로 사라지거나 요청마다 로그가 쌓이지 않게.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class CouponRepositoryImpl implements CouponRepository {

    private final MyRealTripCouponJpaRepository jpaRepository;
    /**
     * 이미 WARN 을 남긴 불량 행의 지문(id + 규칙에 쓰는 컬럼 값). 운영이 SQL 로 고칠 때 {@code updated_at}을 안 바꿀 수 있어
     * 그것 대신 내용으로 판단한다 — 고쳤다가 다시 깨진 행은 값이 달라져 다시 한 번 남는다. 불량 상태의 가짓수만큼만 자란다.
     */
    private final Set<String> warnedRows = ConcurrentHashMap.newKeySet();

    @Override
    public List<Coupon> findActiveOrderByDisplayOrder() {
        List<MyRealTripCouponEntity> rows;
        try {
            rows = jpaRepository.findAllByActiveTrueOrderByDisplayOrderAscCodeAsc();
        } catch (DataAccessException | TransactionException e) {
            throw new BusinessException(ErrorCode.MYREALTRIP_COUPON_LOAD_FAILED, e);
        }
        List<Coupon> coupons = new ArrayList<>(rows.size());
        for (MyRealTripCouponEntity row : rows) {
            try {
                coupons.add(row.toDomain());
            } catch (IllegalArgumentException e) {
                // 도메인 불변식 위반만 그 행을 뺀다. 매핑 버그(NPE·ClassCastException 등)는 삼키지 않는다 — 모든 행이 조용히
                // 빠져 200 + 빈 배열이 되는 대신 전역 핸들러의 500 으로 드러나야 한다.
                String fingerprint = row.getId() + "|" + row.getCode() + "|" + row.getMinimumPrice() + "|"
                        + row.getDiscount() + "|" + row.getStartsOn() + "|" + row.getEndsOn();
                if (warnedRows.add(fingerprint)) {
                    log.warn("myrealtrip_coupons 행이 규칙을 깨 제외한다: id={}, code={}, 사유={}",
                            row.getId(), row.getCode(), e.toString());
                }
            }
        }
        return coupons;
    }
}
