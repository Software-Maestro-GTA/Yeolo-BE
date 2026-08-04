package com.soma.yeolo.course.controller;

import com.soma.yeolo.course.dto.CourseDetailResponse;
import com.soma.yeolo.course.dto.CourseListResponse;
import com.soma.yeolo.course.service.CourseQueryService;
import com.soma.yeolo.global.response.ApiResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 코스 조회 API. 이전 생성 코스 목록(API-FB-10)과 상세(API-FB-7)를 REST로 반환한다. (FUN-7)
 * 코스 생성(SSE)은 {@link CourseController}가 담당한다.
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseQueryController {

    private final CourseQueryService courseQueryService;

    /** 이전 생성 코스 목록 조회 (API-FB-10). 인증 사용자의 코스를 최신순으로 반환한다. */
    @GetMapping
    public ApiResponse<CourseListResponse> getMyCourses(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.success("이전 생성 코스 목록 조회 성공", courseQueryService.getMyCourses(userId));
    }

    /**
     * 코스 상세 조회 (API-FB-7). 코스가 없으면 404, 타인 코스면 403으로 전역 핸들러가 응답한다.
     */
    @GetMapping("/{courseId}")
    public ApiResponse<CourseDetailResponse> getCourse(@AuthenticationPrincipal UUID userId,
                                                       @PathVariable UUID courseId) {
        return ApiResponse.success("여행 코스 조회 성공", courseQueryService.getCourse(userId, courseId));
    }
}
