package com.soma.yeolo.myrealtrip.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.myrealtrip.domain.Coupon;
import com.soma.yeolo.myrealtrip.entity.MyRealTripCouponEntity;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 포트 어댑터 — DB 실패를 명세 500 문구로 번역하고, 규칙을 깨는 행은 그 행만 뺀다. 엔티티는 운영 SQL 로만 채워져 생성자가
 * 없으므로(설계) 테스트에서만 리플렉션으로 필드를 넣는다.
 */
class CouponRepositoryImplTest {

    private final MyRealTripCouponJpaRepository jpa = mock(MyRealTripCouponJpaRepository.class);
    private final CouponRepositoryImpl repository = new CouponRepositoryImpl(jpa);

    @Test
    void DB_접근_실패는_명세의_500_문구다() {
        when(jpa.findAllByActiveTrueOrderByDisplayOrderAscCodeAsc())
                .thenThrow(new DataAccessResourceFailureException("connection refused"));

        assertThatThrownBy(repository::findActiveOrderByDisplayOrder)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MYREALTRIP_COUPON_LOAD_FAILED);
    }

    @Test
    void 커넥션을_못_얻어_트랜잭션_시작이_실패해도_명세의_500_문구다() {
        when(jpa.findAllByActiveTrueOrderByDisplayOrderAscCodeAsc())
                .thenThrow(new CannotCreateTransactionException("Could not open JPA EntityManager for transaction"));

        assertThatThrownBy(repository::findActiveOrderByDisplayOrder)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.MYREALTRIP_COUPON_LOAD_FAILED);
    }

    @Test
    void 규칙을_깨는_행은_그_행만_빼고_나머지를_순서대로_돌려준다() {
        when(jpa.findAllByActiveTrueOrderByDisplayOrderAscCodeAsc()).thenReturn(List.of(
                row("GOOD1", 50000, 1000, null, null, 1),
                row("BAD", 1, 1, LocalDate.parse("2026-12-01"), LocalDate.parse("2026-11-10"), 2),   // 역전된 기간
                row("GOOD2", 100000, 3000, null, null, 3)));

        List<Coupon> coupons = repository.findActiveOrderByDisplayOrder();

        assertThat(coupons).extracting(Coupon::code).containsExactly("GOOD1", "GOOD2");
    }

    private static MyRealTripCouponEntity row(String code, long min, long discount, LocalDate from, LocalDate to, int order) {
        MyRealTripCouponEntity e = org.springframework.beans.BeanUtils.instantiateClass(MyRealTripCouponEntity.class);
        ReflectionTestUtils.setField(e, "code", code);
        ReflectionTestUtils.setField(e, "minimumPrice", min);
        ReflectionTestUtils.setField(e, "discount", discount);
        ReflectionTestUtils.setField(e, "active", true);
        ReflectionTestUtils.setField(e, "startsOn", from);
        ReflectionTestUtils.setField(e, "endsOn", to);
        ReflectionTestUtils.setField(e, "displayOrder", order);
        return e;
    }
}
