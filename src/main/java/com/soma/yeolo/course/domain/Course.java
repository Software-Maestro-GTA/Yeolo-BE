package com.soma.yeolo.course.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 저장용 코스 도메인 (DOM-2). AI가 생성한 코스를 소유자에 연결해 영속하기 위한 순수 모델이다.
 * 목록·상세 조회에 쓰이는 상위 정보는 필드로 승격하고, 일자·방문지 전체는 {@code itineraryJson}
 * (원본 JSON)으로 보존한다. (DOM-2 §5 — {@code constraints}는 명세 개정으로 제거됨)
 *
 * @param userId               소유자 식별자
 * @param title                코스 제목
 * @param destinationCountry   여행 국가
 * @param destinationCity      여행 도시/지역
 * @param startDate            여행 시작일
 * @param totalDays            총 여행 일수
 * @param tags                 코스 태그 (필터용)
 * @param recommendationReason AI 추천 요약 이유
 * @param itineraryJson        일자·방문지·순서 전체 (itinerary 원본 JSON)
 */
public record Course(
        UUID userId,
        String title,
        String destinationCountry,
        String destinationCity,
        LocalDate startDate,
        int totalDays,
        List<String> tags,
        String recommendationReason,
        String itineraryJson
) {
}
