package com.soma.yeolo.course.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 이전 생성 코스 조회 (API-FB-10 목록 / API-FB-7 상세 / FUN-7).
 *
 * <p>목록은 사용자의 코스를 최신순 요약으로 반환하고, 상세는 소유권을 검증한 뒤 일자·방문지 전체를
 * 포함해 반환한다. 코스 없음은 404, 타인 코스 접근은 403으로 노출한다(전역 핸들러).
 */
@Service
@RequiredArgsConstructor
public class CourseQueryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CourseRepository courseRepository;

    /** 사용자의 이전 생성 코스를 최신순 요약 목록으로 반환한다. 없으면 빈 목록. (API-FB-10) */
    public CourseListResponse getMyCourses(UUID userId) {
        return CourseListResponse.from(courseRepository.findByUserIdLatestFirst(userId));
    }

    /**
     * 코스 상세를 반환한다. 코스가 없으면 404, 소유자가 아니면 403으로 응답한다. (API-FB-7)
     */
    public CourseDetailResponse getCourse(UUID userId, UUID courseId) {
        SavedCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.COURSE_ACCESS_DENIED);
        }
        return CourseDetailResponse.from(course, parseItinerary(course.itineraryJson()));
    }

    private JsonNode parseItinerary(String itineraryJson) {
        try {
            return OBJECT_MAPPER.readTree(itineraryJson);
        } catch (Exception e) {
            // 저장된 itinerary JSON이 손상된 예외적 상황 — 500으로 노출한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
