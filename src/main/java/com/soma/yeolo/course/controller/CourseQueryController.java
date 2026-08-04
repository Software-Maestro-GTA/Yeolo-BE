package com.soma.yeolo.course.controller;

import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.service.CourseQueryService;
import com.soma.yeolo.global.response.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 코스 조회·관리 API. 코스 목록(API-COURSE-3)과 상세(API-COURSE-2)를 REST로 반환하고,
 * 코스 삭제(API-COURSE-4)를 처리한다. (FUN-9) 코스 생성(SSE)은 {@link CourseController}가 담당한다.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseQueryController {

    private final CourseQueryService courseQueryService;

    /** 여행 코스 목록 조회 (API-COURSE-3). 인증 사용자의 코스를 최신순으로 반환한다. */
    @GetMapping
    public ApiResponse<CourseListResponse> getMyCourses(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.success("여행 코스 목록 조회 성공", courseQueryService.getMyCourses(userId));
    }

    /**
     * 코스 상세 조회 (API-COURSE-2). 코스가 없으면 404, 타인 코스면 403으로 전역 핸들러가 응답한다.
     */
    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> getCourse(@AuthenticationPrincipal UUID userId,
                                                       @PathVariable UUID courseId) {
        return ApiResponse.success("여행 코스 조회 성공", courseQueryService.getCourse(userId, courseId));
    }

    /**
     * 코스 삭제 (API-COURSE-4). 소유자만 삭제할 수 있으며, 삭제 후 목록·상세 조회에서 제외된다.
     * 코스가 없으면 404, 타인 코스면 403으로 전역 핸들러가 응답한다.
     */
    @DeleteMapping("/{courseId}")
    public ApiResponse<Void> deleteCourse(@AuthenticationPrincipal UUID userId,
                                          @PathVariable UUID courseId) {
        courseQueryService.deleteCourse(userId, courseId);
        return ApiResponse.success("여행 코스 삭제 성공", null);
    }
}
