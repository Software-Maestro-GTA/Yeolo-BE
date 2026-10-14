package com.soma.yeolo.course.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 신규 저장용 코스 (DOM-3). AI가 산출한 코스 중 목록·상세에 쓰이는 상위 정보를 승격해 담고,
 * 일자·방문지 전체는 {@code itineraryJson}(원본 JSON)으로 보존한다.
 *
 * <p>명세 개정(2026-10): {@code recommendationReason}은 FE·AI 계약 모두에서 빠져 제거했고,
 * {@code totalCost}가 추가됐다. {@code coverImageUrl}은 AI 계약(API-AI-2)에서는 빠졌지만 목록
 * 응답(API-COURSE-3)에는 남아 있어 유지한다 — AI가 주면 담고, 아니면 null이다.
 *
 * @param userId             소유자 식별자
 * @param title              코스 제목
 * @param destinationCountry 여행 국가
 * @param destinationCity    여행 도시
 * @param coverImageUrl      코스 대표 이미지 URL (없으면 null)
 * @param startDate          여행 시작일
 * @param totalDays          총 여행 일수
 * @param totalCost          코스 전체 예상 비용 (AI가 주지 않으면 null)
 * @param tags               코스 태그
 * @param itineraryJson      일자·방문지·순서 전체 (itinerary 원본 JSON)
 */
public record Course(
        UUID userId,
        String title,
        String destinationCountry,
        String destinationCity,
        String coverImageUrl,
        LocalDate startDate,
        int totalDays,
        Long totalCost,
        List<String> tags,
        String itineraryJson
) {
}
