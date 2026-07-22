package com.soma.yeolo.course.dto;

import com.soma.yeolo.course.domain.SavedCourse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 이전 생성 코스 목록 조회 응답의 {@code data} 페이로드 (API-FB-10). 상세(itinerary)를 제외한
 * 요약 메타데이터만 최신순으로 담는다.
 */
public record CourseListResponse(List<CourseSummary> courses) {

    /** 목록 항목 요약. 필드명은 명세 그대로 사용한다. */
    public record CourseSummary(
            String courseId,
            String title,
            String destinationCountry,
            String destinationCity,
            LocalDate startDate,
            int totalDays,
            List<String> tags,
            String recommendationReason,
            Instant createdAt
    ) {

        public static CourseSummary from(SavedCourse course) {
            return new CourseSummary(
                    course.courseId().toString(),
                    course.title(),
                    course.destinationCountry(),
                    course.destinationCity(),
                    course.startDate(),
                    course.totalDays(),
                    course.tags(),
                    course.recommendationReason(),
                    course.createdAt()
            );
        }
    }

    public static CourseListResponse from(List<SavedCourse> courses) {
        return new CourseListResponse(courses.stream().map(CourseSummary::from).toList());
    }
}
