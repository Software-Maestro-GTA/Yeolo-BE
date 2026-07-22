package com.soma.yeolo.course.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.course.domain.SavedCourse;
import java.time.LocalDate;
import java.util.List;

/**
 * 여행 코스 상세 조회 응답의 {@code data} 페이로드 (API-FB-7). 요약 정보에 더해 일자·방문지 전체
 * ({@code itinerary})를 원본 JSON 구조 그대로 담는다.
 */
public record CourseDetailResponse(CourseDetail course) {

    /** 코스 상세. 필드명은 명세 그대로 사용하며, {@code itinerary}는 저장된 원본 JSON을 그대로 전달한다. */
    public record CourseDetail(
            String courseId,
            String userId,
            String title,
            String destinationCountry,
            String destinationCity,
            LocalDate startDate,
            int totalDays,
            List<String> tags,
            String recommendationReason,
            JsonNode itinerary
    ) {
    }

    /** 읽기 모델과 파싱된 itinerary 노드로 상세 응답을 조립한다. */
    public static CourseDetailResponse from(SavedCourse course, JsonNode itinerary) {
        return new CourseDetailResponse(new CourseDetail(
                course.courseId().toString(),
                course.userId().toString(),
                course.title(),
                course.destinationCountry(),
                course.destinationCity(),
                course.startDate(),
                course.totalDays(),
                course.tags(),
                course.recommendationReason(),
                itinerary
        ));
    }
}
