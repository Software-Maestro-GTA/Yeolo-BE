package com.soma.yeolo.tasteprofile.controller;

import com.soma.yeolo.consent.service.PhotoAnalysisConsentChecker;
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
 * 성향 프로필 API. 이미지 메타데이터 기반 성향 분석을 SSE로 스트리밍한다. (API-FB-2)
 */
@RestController
@RequestMapping("/api/taste-profile")
@RequiredArgsConstructor
public class TasteProfileController {

    private final BehaviorTasteProfileService behaviorTasteProfileService;
    private final PhotoAnalysisConsentChecker photoAnalysisConsentChecker;
    private final SseProperties sseProperties;

    @org.springframework.beans.factory.annotation.Qualifier("sseTaskExecutor")
    private final AsyncTaskExecutor sseTaskExecutor;

    /**
     * 이미지 메타데이터 기반 성향 분석 생성 (API-FB-2).
     * 요청 검증 실패(빈 목록/형식 오류)는 스트림 시작 전 400 JSON으로 응답한다(전역 핸들러).
     *
     * <p>사진 분석 동의 검증도 같은 이유로 <b>스트림을 열기 전</b>에 수행한다(REQ-8 / API-PREF-3의 403).
     * 동의가 없으면 분석 파이프라인을 아예 시작하지 않으므로, error 이벤트가 아니라 명세의 실패 응답
     * 형태 그대로 403 JSON으로 나간다.
     */
    @PostMapping(value = "/behavior", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeBehavior(@AuthenticationPrincipal UUID userId,
                                      @Valid @RequestBody BehaviorAnalysisRequest request) {
        photoAnalysisConsentChecker.requireAgreed(userId);

        SseEmitter emitter =
                SseEmitters.create("taste-profile", sseProperties.streamTimeoutMs(), userId);
        sseTaskExecutor.execute(() -> behaviorTasteProfileService.analyzeAndStream(userId, request, emitter));
        return emitter;
    }
}
