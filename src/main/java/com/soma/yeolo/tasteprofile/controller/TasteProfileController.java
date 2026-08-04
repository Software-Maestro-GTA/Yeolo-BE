package com.soma.yeolo.tasteprofile.controller;

import com.soma.yeolo.global.sse.SseEmitters;
import com.soma.yeolo.global.sse.SseProperties;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest;
import com.soma.yeolo.tasteprofile.service.BehaviorTasteProfileService;
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
 * 성향 프로필 API. 이미지 메타데이터 기반 취향 분석을 SSE로 스트리밍한다. (API-PREF-3)
 */
@RestController
@RequestMapping("/api/users/me/taste-profile")
@RequiredArgsConstructor
public class TasteProfileController {

    private final BehaviorTasteProfileService behaviorTasteProfileService;
    private final SseProperties sseProperties;

    @org.springframework.beans.factory.annotation.Qualifier("sseTaskExecutor")
    private final AsyncTaskExecutor sseTaskExecutor;

    /**
     * 이미지 메타데이터 기반 취향 분석 생성 (API-PREF-3).
     * 요청 검증 실패(빈 목록/형식 오류)는 스트림 시작 전 400 JSON으로 응답한다(전역 핸들러).
     */
    @PostMapping(value = "/analysis", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeBehavior(@AuthenticationPrincipal UUID userId,
                                      @Valid @RequestBody BehaviorAnalysisRequest request) {
        SseEmitter emitter =
                SseEmitters.create("taste-profile", sseProperties.streamTimeoutMs(), userId);
        sseTaskExecutor.execute(() -> behaviorTasteProfileService.analyzeAndStream(userId, request, emitter));
        return emitter;
    }
}
