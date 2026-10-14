package com.soma.yeolo.course.dto;

import com.soma.yeolo.course.domain.SavedCourse;
import java.time.LocalDate;
import java.util.List;

/**
 * 여행 코스 상세 응답 (API-COURSE-2). 명세 개정(2026-10)으로 {@code coverImageUrl}·
 * {@code recommendationReason}이 빠지고 {@code totalCost}가 추가됐다.
 * {@code totalCostExRate}·{@code exRateIcon}(환율 환산)은 #100에서 추가한다.
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
            List<String> tags,
            Itinerary itinerary
    ) {
    }

    public static CourseDetailResponse from(SavedCourse course, Itinerary itinerary) {
        return new CourseDetailResponse(new CourseDetail(
                course.courseId().toString(),
                course.userId().toString(),
                course.title(),
                course.destinationCountry(),
                course.destinationCity(),
                course.startDate(),
                course.totalDays(),
                course.totalCost(),
                course.tags(),
                itinerary
        ));
    }
}
