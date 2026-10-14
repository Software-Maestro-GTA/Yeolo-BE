package com.soma.yeolo.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.service.port.ExchangeRateProvider;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * fawazahmed0/currency-api 어댑터 ({@link ExchangeRateProperties} 참고).
 *
 * <p>응답은 {@code {"date":"2026-10-14","jpy":{"krw":8.49,"usd":0.0063,...}}} 꼴이라 기준 통화 하나당
 * 전 통화 환율이 한 번에 온다. 통화별로 캐시해 두고 TTL 안에서는 재호출하지 않는다. 갱신에 실패하면
 * 두 번째 호스트를 시도하고, 그것도 실패하면 <b>지난 성공값</b>을 돌려준다(환율은 하루 단위 참고값이라
 * 어제 값이 없는 것보다 낫다). 성공한 적이 없으면 빈 값이다.
 *
 * <p>실패 뒤에는 {@link #RETRY_INTERVAL} 동안 다시 시도하지 않는다 — 제공처 장애 중에 코스 상세 요청마다
 * 두 호스트의 타임아웃(최대 수 초)을 기다리면 상세 조회 자체가 느려진다. 조회는 잠금 없이 하므로 같은
 * 통화를 동시에 갱신하면 중복 호출이 날 수 있지만, 응답이 같은 값이라 무해하다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "exchange-rate.provider", havingValue = "currency-api")
public class CurrencyApiExchangeRateClient implements ExchangeRateProvider {

    static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final ExchangeRateProperties properties;
    private final Map<String, CachedRates> cache = new ConcurrentHashMap<>();

    public CurrencyApiExchangeRateClient(@Qualifier("restClient") RestClient restClient,
                                        ExchangeRateProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public Optional<BigDecimal> rate(String from, String to) {
        if (from == null || to == null || from.isBlank() || to.isBlank()) {
            return Optional.empty();
        }
        String base = from.strip().toLowerCase(Locale.ROOT);
        String quote = to.strip().toLowerCase(Locale.ROOT);
        if (base.equals(quote)) {
            return Optional.of(BigDecimal.ONE);
        }
        return Optional.ofNullable(current(base).rates().get(quote));
    }

    /** 신선하면 그대로, 아니면(백오프 중이 아닐 때만) 갱신을 시도한 뒤 최신 상태를 돌려준다. */
    private CachedRates current(String base) {
        Instant now = Instant.now();
        CachedRates cached = cache.get(base);
        if (cached != null) {
            if (cached.fetchedAt() != null && cached.fetchedAt().plus(properties.cacheTtl()).isAfter(now)) {
                return cached;
            }
            if (cached.lastFailureAt() != null && cached.lastFailureAt().plus(RETRY_INTERVAL).isAfter(now)) {
                return cached;   // 방금 실패했다 — 지난 값(또는 빈 표)으로 버틴다
            }
        }
        Map<String, BigDecimal> fetched = fetch(properties.baseUrl(), base);
        if (fetched == null && properties.fallbackUrl() != null && !properties.fallbackUrl().isBlank()) {
            fetched = fetch(properties.fallbackUrl(), base);
        }
        CachedRates next;
        if (fetched != null) {
            next = new CachedRates(now, null, fetched);
        } else if (cached != null && cached.fetchedAt() != null) {
            log.warn("환율 갱신에 실패해 지난 값을 계속 쓴다. base={}, fetchedAt={}", base, cached.fetchedAt());
            next = new CachedRates(cached.fetchedAt(), now, cached.rates());
        } else {
            next = new CachedRates(null, now, Map.of());
        }
        cache.put(base, next);
        return next;
    }

    /** 실패하면 null — 호출자가 폴백·지난 값으로 이어 간다. */
    private Map<String, BigDecimal> fetch(String baseUrl, String base) {
        String url = baseUrl.strip().replaceAll("/+$", "") + "/" + base + ".min.json";
        try {
            String body = restClient.get().uri(url).retrieve().body(String.class);
            JsonNode root = OBJECT_MAPPER.readTree(body);
            JsonNode table = root.get(base);
            if (table == null || !table.isObject()) {
                log.warn("환율 응답에 {} 표가 없다: {}", base, url);
                return null;
            }
            Map<String, BigDecimal> rates = new HashMap<>();
            table.fields().forEachRemaining(entry -> {
                if (entry.getValue().isNumber()) {
                    rates.put(entry.getKey(), entry.getValue().decimalValue());
                }
            });
            return rates;
        } catch (Exception e) {
            log.warn("환율 조회 실패: {} ({})", url, e.toString());
            return null;
        }
    }

    /**
     * @param fetchedAt     마지막 성공 시각. 성공한 적이 없으면 null
     * @param lastFailureAt 마지막 실패 시각. 백오프 기준이며 성공하면 지운다
     * @param rates         마지막 성공 응답. 성공한 적이 없으면 빈 표
     */
    private record CachedRates(Instant fetchedAt, Instant lastFailureAt, Map<String, BigDecimal> rates) {
    }
}
