package com.soma.yeolo.tasteprofile.dto;

import java.util.List;

/**
 * 취향 조회 응답 본문 (API-PREF-4, 2026-10 개정). 응답 {@code data}가 이 객체 자체다 — 개정 전의
 * {@code tasteProfile} 래퍼와 8개 영역 지표는 FE 계약에서 빠졌다(지표는 코스 생성용으로 저장만 한다).
 *
 * <p>저장된 AI 요약 JSON(API-AI-1 {@code summary})을 이 타입으로 역직렬화해 돌려준다. AI 가 덤으로 준
 * 미지 필드는 무시되고 <b>여기 선언한 필드만 FE 로 나간다</b>. {@code recommendCity.imageUrl}은 AI 가
 * 주지 않으므로 BE 가 도시 사진 데이터셋(#97)에서 채운다 — 없으면 빈 문자열이다(명세 타입이 string).
 */
public record TasteSummaryResponse(
        String intro,
        List<String> hashtags,
        List<TasteType> types,
        RecommendCity recommendCity
) {

    /** 취향 유형 축 하나. {@code score}는 {@code lowName}↔{@code highName} 사이의 위치다. */
    public record TasteType(
            String typeCode,
            String typeName,
            String lowName,
            String highName,
            Double score
    ) {
    }

    public record RecommendCity(
            String countryName,
            String cityName,
            String imageUrl,
            String reason
    ) {
        public RecommendCity withImageUrl(String imageUrl) {
            return new RecommendCity(countryName, cityName, imageUrl, reason);
        }
    }

    public TasteSummaryResponse withRecommendCity(RecommendCity recommendCity) {
        return new TasteSummaryResponse(intro, hashtags, types, recommendCity);
    }
}
