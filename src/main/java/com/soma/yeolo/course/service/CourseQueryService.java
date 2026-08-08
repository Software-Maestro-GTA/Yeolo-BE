package com.soma.yeolo.course.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.domain.SavedCourse;
import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.dto.Itinerary;
import com.soma.yeolo.course.service.port.CourseRepository;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 코스 조회·관리 (API-COURSE-3 목록 / API-COURSE-2 상세 / API-COURSE-4 삭제 / FUN-9).
 *
 * <p>목록은 사용자의 코스를 최신순 요약으로 반환하고, 상세는 소유권을 검증한 뒤 일자·방문지 전체를
 * 포함해 반환한다. 삭제 역시 소유권을 검증한 뒤 제거한다. 코스 없음은 404, 타인 코스 접근은 403으로
 * 노출한다(전역 핸들러).
 */
@Service
@RequiredArgsConstructor
public class CourseQueryService {

    // 저장된 원본 itinerary JSON에 명세에 없는 필드가 섞일 수 있으므로 미지 필드는 무시하고 역직렬화한다.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final CourseRepository courseRepository;

    /** 사용자의 이전 생성 코스를 최신순 요약 목록으로 반환한다. 없으면 빈 목록. (API-COURSE-3) */
    @Transactional(readOnly = true)
    public CourseListResponse getMyCourses(UUID userId) {
        return CourseListResponse.from(courseRepository.findByUserIdLatestFirst(userId));
    }

    /**
     * 코스 상세를 반환한다. 코스가 없으면 404, 소유자가 아니면 403으로 응답한다. (API-COURSE-2)
     */
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourse(UUID userId, UUID courseId) {
        SavedCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.COURSE_ACCESS_DENIED);
        }
        return CourseDetailResponse.from(course, parseItinerary(course.itineraryJson()));
    }

    /**
     * 코스를 삭제한다. 코스가 없으면 404, 소유자가 아니면 403으로 응답한다. 삭제 후에는 목록·상세
     * 조회에서 제외된다. (API-COURSE-4)
     *
     * <p>없는 코스와 타인 코스를 다른 코드로 구분해 알려 준다 — 조회(API-COURSE-2)가 이미 같은
     * 방식이라 삭제만 감출 실익이 없고, 명세가 404·403을 모두 정의하고 있다.
     */
    @Transactional
    public void deleteCourse(UUID userId, UUID courseId) {
        SavedCourse course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.COURSE_DELETE_ACCESS_DENIED);
        }
        courseRepository.deleteById(courseId);
    }

    private Itinerary parseItinerary(String itineraryJson) {
        try {
            return OBJECT_MAPPER.readValue(itineraryJson, Itinerary.class);
        } catch (Exception e) {
            // 저장된 itinerary JSON이 손상된 예외적 상황 — 500으로 노출한다.
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, e);
        }
    }
}
