package com.soma.yeolo.course.controller;

import com.soma.yeolo.course.dto.CourseCreationRequest;
import com.soma.yeolo.course.service.CourseCreationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

    /** SSE 타임아웃. 성향 로딩 + AI 코스 생성 시간을 고려해 여유 있게 두되 무한 대기는 방지한다. */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final CourseCreationService courseCreationService;
    private final AsyncTaskExecutor sseTaskExecutor;

    /**
     * 코스 생성 요청 (API-FB-4). 요청 검증 실패(누락/형식 오류)는 스트림 시작 전 400 JSON으로
     * 응답한다(전역 핸들러).
     */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter createCourse(@AuthenticationPrincipal UUID userId,
                                   @Valid @RequestBody CourseCreationRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // emitter 참조가 방치되지 않도록 종료/타임아웃/에러 수명주기 콜백을 등록한다.
        // (블로킹 AI 호출은 도중 취소가 불가하므로, 콜백은 정리·로깅까지만 책임진다.)
        emitter.onTimeout(() -> {
            log.warn("SSE 타임아웃 - course 스트림 (userId={})", userId);
            emitter.complete();
        });
        emitter.onError(e -> log.warn("SSE 에러 - course 스트림 (userId={}): {}", userId, e.toString()));
        emitter.onCompletion(() -> log.debug("SSE 종료 - course 스트림 (userId={})", userId));
        sseTaskExecutor.execute(() -> courseCreationService.createAndStream(userId, request, emitter));
        return emitter;
    }
}
