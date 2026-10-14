package com.soma.yeolo.course.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 환율 제공처 설정 (#100).
 *
 * <p>제공처는 fawazahmed0/currency-api 다 — 키·출처 표시가 필요 없고 통화 340개를 매일 갱신하며
 * jsDelivr CDN 으로 서빙된다. ECB 기반(frankfurter)은 통화가 30여 개라 베트남 동(VND)·대만 달러(TWD)
 * 등 주요 여행지가 빠지고, open.er-api 는 무료 티어에 출처 링크 표시를 요구해 제외했다.
 *
 * <p>{@code currency-api}를 고르면 {@code baseUrl}·{@code cacheTtl}은 기동 시 검증한다 — 빈 값
 * ({@code EXCHANGE_RATE_BASE_URL=} 처럼 설정돼 있으나 비어 있는 경우)으로 기동되면 코스 상세가 NPE 로
 * 500 이 되는데, 그건 "환율 실패는 상세를 실패시키지 않는다"는 포트 계약 위반이다.
 *
 * @param sourceCurrency AI 가 {@code totalCost}를 매기는 통화(ISO 4217). AI 는 목적지와 무관하게 <b>원화</b>로
 *                       준다(AI 팀 확인, 2026-10-14). 코스 상세는 이 통화에서 목적지 현지 통화로 환산한다
 * @param provider    {@code currency-api} 또는 {@code stub}(환율 없음 — 테스트·로컬 기본값)
 * @param baseUrl     {@code {baseUrl}/{from}.min.json} 형태로 호출한다 (CDN)
 * @param fallbackUrl CDN 장애 시 같은 데이터의 두 번째 호스트 (Cloudflare Pages). 비우면 폴백 없음
 * @param cacheTtl    통화별 응답 캐시 수명. 데이터가 하루 한 번 갱신되므로 길게 둔다
 */
@ConfigurationProperties(prefix = "exchange-rate")
public record ExchangeRateProperties(String sourceCurrency, String provider, String baseUrl, String fallbackUrl,
                                     Duration cacheTtl) {

    public ExchangeRateProperties {
        if (sourceCurrency == null || !sourceCurrency.strip().toUpperCase(java.util.Locale.ROOT).matches("[A-Z]{3}")) {
            throw new IllegalStateException("exchange-rate.source-currency 는 ISO 4217 코드여야 한다: '" + sourceCurrency + "'");
        }
        sourceCurrency = sourceCurrency.strip().toUpperCase(java.util.Locale.ROOT);
        if ("currency-api".equals(provider)) {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("exchange-rate.base-url 이 비어 있다 (provider=currency-api)");
            }
            if (cacheTtl == null || cacheTtl.isNegative()) {
                throw new IllegalStateException("exchange-rate.cache-ttl 이 비어 있거나 음수다 (provider=currency-api)");
            }
        }
    }
}
