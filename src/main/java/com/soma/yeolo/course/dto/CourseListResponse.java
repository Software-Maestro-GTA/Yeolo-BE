package com.soma.yeolo.course.dto;

import com.soma.yeolo.course.domain.SavedCourse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 여행 코스 목록 응답 (API-COURSE-3). 명세 개정(2026-10)으로 {@code recommendationReason}이 빠졌다.
 * {@code coverImageUrl}은 목록에만 남아 있다(상세 API-COURSE-2에서는 제거됨).
 */
public record CourseListResponse(List<CourseSummary> courses) {

    public record CourseSummary(
            String courseId,
            String title,
            String destinationCountry,
            String destinationCity,
            String coverImageUrl,
            LocalDate startDate,
            int totalDays,
            List<String> tags,
            Instant createdAt
    ) {
        public static CourseSummary from(SavedCourse course) {
            return new CourseSummary(
                    course.courseId().toString(),
                    course.title(),
                    course.destinationCountry(),
                    course.destinationCity(),
                    course.coverImageUrl(),
                    course.startDate(),
                    course.totalDays(),
                    course.tags(),
                    course.createdAt()
            );
        }
    }

    public static CourseListResponse from(List<SavedCourse> courses) {
        return new CourseListResponse(courses.stream().map(CourseSummary::from).toList());
    }
}
