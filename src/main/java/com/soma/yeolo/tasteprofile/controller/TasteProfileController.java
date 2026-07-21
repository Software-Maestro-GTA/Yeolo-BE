package com.soma.yeolo.tasteprofile.controller;

import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest;
import com.soma.yeolo.tasteprofile.service.BehaviorTasteProfileService;
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
 * 성향 프로필 API. 이미지 메타데이터 기반 성향 분석을 SSE로 스트리밍한다. (API-FB-2)
 */
@Slf4j
@RestController
@RequestMapping("/api/taste-profile")
@RequiredArgsConstructor
public class TasteProfileController {

    /** SSE 타임아웃. AI 분석 시간을 고려해 여유 있게 두되 무한 대기는 방지한다. */
    private static final long SSE_TIMEOUT_MS = 120_000L;

    private final BehaviorTasteProfileService behaviorTasteProfileService;
    private final AsyncTaskExecutor sseTaskExecutor;

    /**
     * 이미지 메타데이터 기반 성향 분석 생성 (API-FB-2).
     * 요청 검증 실패(빈 목록/형식 오류)는 스트림 시작 전 400 JSON으로 응답한다(전역 핸들러).
     */
    @PostMapping(value = "/behavior", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeBehavior(@AuthenticationPrincipal UUID userId,
                                      @Valid @RequestBody BehaviorAnalysisRequest request) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // emitter 참조가 방치되지 않도록 종료/타임아웃/에러 수명주기 콜백을 등록한다.
        // (블로킹 AI 호출은 도중 취소가 불가하므로, 콜백은 정리·로깅까지만 책임진다.)
        emitter.onTimeout(() -> {
            log.warn("SSE 타임아웃 - taste-profile 스트림 (userId={})", userId);
            emitter.complete();
        });
        emitter.onError(e -> log.warn("SSE 에러 - taste-profile 스트림 (userId={}): {}", userId, e.toString()));
        emitter.onCompletion(() -> log.debug("SSE 종료 - taste-profile 스트림 (userId={})", userId));
        sseTaskExecutor.execute(() -> behaviorTasteProfileService.analyzeAndStream(userId, request, emitter));
        return emitter;
    }
}
