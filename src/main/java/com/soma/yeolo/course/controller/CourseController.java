package com.soma.yeolo.course.controller;

import com.soma.yeolo.course.dto.CourseCreationRequest;
import com.soma.yeolo.course.service.CourseCreationService;
import com.soma.yeolo.global.sse.SseEmitters;
import com.soma.yeolo.global.sse.SseProperties;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 코스 API. 지역/날짜/예산 조건 기반 코스 생성을 SSE로 스트리밍한다. (API-FB-4 / FUN-6)
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseCreationService courseCreationService;
    private final AsyncTaskExecutor sseTaskExecutor;
    private final SseProperties sseProperties;

    /**
     * 코스 생성 요청 (API-FB-4). 요청 검증 실패(누락/형식 오류)는 스트림 시작 전 400 JSON으로
     * 응답한다(전역 핸들러).
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createCourse(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody CourseCreationRequest request) {
        SseEmitter emitter = SseEmitters.create("course", sseProperties.streamTimeoutMs(), userId);
        sseTaskExecutor.execute(() -> courseCreationService.createAndStream(userId, request, emitter));
        return emitter;
    }
}
