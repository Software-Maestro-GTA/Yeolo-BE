package com.soma.yeolo.course.dto;

import com.soma.yeolo.course.domain.SavedCourse;
import java.time.LocalDate;
import java.util.List;

/**
 * 여행 코스 상세 응답 (API-COURSE-2). 명세 개정(2026-10)으로 {@code coverImageUrl}·
 * {@code recommendationReason}이 빠지고 {@code totalCost}·{@code totalCostExRate}·{@code exRateIcon}이
 * 추가됐다.
 *
 * <p>{@code totalCost}는 AI 가 준 <b>원화</b> 금액이고, {@code totalCostExRate}는 목적지 현지 통화로 환산한
 * 금액, {@code exRateIcon}은 그 현지 통화의 기호(¥·$·฿ — 국가 데이터셋)다. 환율·통화를 못 구하면 환산값은
 * null, 기호는 빈 문자열이다 — 비용은 참고값이라 조회를 실패시키지 않는다.
 */
public record CourseDetailResponse(CourseDetail course) {

    public record CourseDetail(
            String courseId,
            String userId,
            String title,
            String destinationCountry,
            String destinationCity,
            LocalDate startDate,
            int totalDays,
            Long totalCost,
            Long totalCostExRate,
            String exRateIcon,
            List<String> tags,
            Itinerary itinerary
    ) {
    }

    /** 환산 결과. 환율을 못 구했으면 {@code totalCostExRate}는 null, 통화를 모르면 {@code exRateIcon}은 빈 문자열. */
    public record CostConversion(Long totalCostExRate, String exRateIcon) {
        public static final CostConversion NONE = new CostConversion(null, "");
    }

    public static CourseDetailResponse from(SavedCourse course, Itinerary itinerary, CostConversion conversion) {
        return new CourseDetailResponse(new CourseDetail(
                course.courseId().toString(),
                course.userId().toString(),
                course.title(),
                course.destinationCountry(),
                course.destinationCity(),
                course.startDate(),
                course.totalDays(),
                course.totalCost(),
                conversion.totalCostExRate(),
                conversion.exRateIcon(),
                course.tags(),
                itinerary
        ));
    }
}
