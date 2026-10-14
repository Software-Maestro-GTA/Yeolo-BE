package com.soma.yeolo.location.service;

import java.util.Optional;

/**
 * 국가명으로 현지 통화를 찾는 포트 (API-COURSE-2 {@code exRateIcon}·{@code totalCostExRate}, #100).
 * 근거 데이터는 {@code countries.tsv}의 통화 코드·기호 열이다(docs/location-dataset.md).
 */
public interface CountryCurrencyReader {

    /**
     * @param code   ISO 4217 코드 (예: JPY)
     * @param symbol 현지 통화 기호 (예: ¥). CLDR 좁은 기호 우선, 없으면 코드와 같다
     */
    record CountryCurrency(String code, String symbol) {
    }

    /**
     * @param countryName 표시명(예: "일본"). 적재된 국가명과 정규화 후 완전 일치해야 한다
     * @return 국가를 못 찾거나 통용 통화가 없는 영토(남극 등)면 빈 값
     */
    Optional<CountryCurrency> findCurrency(String countryName);
}
