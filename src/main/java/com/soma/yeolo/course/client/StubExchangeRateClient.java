package com.soma.yeolo.course.client;

import com.soma.yeolo.course.service.port.ExchangeRateProvider;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 환율 없음 스텁 ({@code exchange-rate.provider=stub}, 설정이 없을 때의 기본값). 외부 호출 없이 돌리는
 * 테스트·로컬용으로, 같은 통화면 1, 아니면 빈 값이라 코스 상세의 {@code totalCostExRate}가 null 로 나간다.
 * 다른 provider 스위치(geocode·place·profile-image)와 같이 네트워크를 타는 쪽은 명시해야만 켜진다.
 */
@Component
@ConditionalOnProperty(name = "exchange-rate.provider", havingValue = "stub", matchIfMissing = true)
public class StubExchangeRateClient implements ExchangeRateProvider {

    @Override
    public Optional<BigDecimal> rate(String from, String to) {
        return from != null && from.equalsIgnoreCase(to) ? Optional.of(BigDecimal.ONE) : Optional.empty();
    }
}
