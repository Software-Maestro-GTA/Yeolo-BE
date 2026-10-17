package com.soma.yeolo.tasteprofile.client.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * AI 취향 분석의 complete 페이로드 (API-AI-1). 내부 파이프라인은 무손실 보존을 위해 {@link JsonNode}를
 * 유지한다(CLAUDE.md §코드 컨벤션 — FE 경계에서만 DTO 로 타입을 명시).
 *
 * @param tasteProfile 8개 영역의 취향 지표. 코스 생성(API-AI-2)에 그대로 실린다. 필수
 * @param summary      2026-10 개정으로 추가된 요약(intro·hashtags·types·recommendCity). 취향 조회
 *                     (API-PREF-4)의 응답 본문이다. 개정 전 AI 는 주지 않으므로 null 일 수 있다
 */
public record AiTasteAnalysis(JsonNode tasteProfile, JsonNode summary) {
}
