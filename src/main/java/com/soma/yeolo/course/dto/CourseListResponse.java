package com.soma.yeolo.course.dto;

import com.soma.yeolo.course.domain.SavedCourse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

/**
 * 여행 코스 목록 응답 (API-COURSE-3). 명세 개정(2026-10)으로 {@code recommendationReason}이 빠졌다.
 * {@code coverImageUrl}은 목록에만 남아 있다(상세 API-COURSE-2에서는 제거됨). AI 가 더 이상 대표
 * 이미지를 주지 않으므로(API-AI-2 개정) 저장값이 없으면 호출자가 목적지 도시 사진으로 채운다.
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
        public static CourseSummary from(SavedCourse course, String coverImageUrl) {
            return new CourseSummary(
                    course.courseId().toString(),
                    course.title(),
                    course.destinationCountry(),
                    course.destinationCity(),
                    coverImageUrl,
                    course.startDate(),
                    course.totalDays(),
                    course.tags(),
                    course.createdAt()
            );
        }
    }

    /**
     * @param coverImage 코스별 대표 이미지 URL 공급자. 저장값이 있으면 그것을, 없으면 목적지 도시
     *                   사진을, 그것도 없으면 null 을 돌려준다
     */
    public static CourseListResponse from(List<SavedCourse> courses, Function<SavedCourse, String> coverImage) {
        return new CourseListResponse(courses.stream()
                .map(course -> CourseSummary.from(course, coverImage.apply(course)))
                .toList());
    }
}
