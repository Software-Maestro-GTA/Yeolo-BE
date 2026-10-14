package com.soma.yeolo.course.service.port;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * 환율 조회 포트 (API-COURSE-2 {@code totalCostExRate}, #100). 구현은 {@code course.client}에 있다.
 *
 * <p>환율은 계획용 참고값이다. 못 구하면 빈 값을 돌려주고 호출자는 환산값을 null 로 내린다 —
 * 외부 환율 서비스 장애가 코스 상세 조회를 실패시키지 않는다.
 */
public interface ExchangeRateProvider {

    /**
     * @param from ISO 4217 통화 코드 (현지 통화, 예: JPY)
     * @param to   ISO 4217 통화 코드 (기준 통화, 예: KRW)
     * @return {@code from} 1 단위가 {@code to} 로 얼마인지. 같은 통화면 1, 모르면 빈 값
     */
    Optional<BigDecimal> rate(String from, String to);
}
