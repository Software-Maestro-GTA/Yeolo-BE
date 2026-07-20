package com.soma.yeolo.tasteprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.tasteprofile.client.AiTasteProfileClient;
import com.soma.yeolo.tasteprofile.client.dto.AiBehaviorAnalysisRequest;
import com.soma.yeolo.tasteprofile.domain.PreprocessedImage;
import com.soma.yeolo.tasteprofile.domain.SourceType;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisEvents.CompleteData;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisEvents.Progress;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileStore;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 이미지 메타데이터 기반 성향 분석 오케스트레이션 (API-FB-2 / FUN-1).
 *
 * <p>전처리(Reverse Geocode + 시간 맥락) → AI 분석 호출(API-BA-6) → 결과 저장(DOM-1) 순으로
 * 진행하며, 각 단계 상태를 {@link SseEmitter}로 스트리밍한다. 이벤트 step명은 명세 그대로 사용한다.
 * 이 메서드는 비동기 워커 스레드에서 실행되어 전체 SSE 수명주기를 책임진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorTasteProfileService {

    private static final String EVENT_PROGRESS = "progress";
    private static final String EVENT_COMPLETE = "complete";
    private static final String EVENT_ERROR = "error";

    private final ImageMetadataPreprocessor preprocessor;
    private final AiTasteProfileClient aiClient;
    private final TasteProfileAssembler assembler;
    private final TasteProfileStore tasteProfileStore;

    /** 전처리 → AI 분석 → 저장 파이프라인을 실행하고 진행 상황을 SSE로 중계한다. */
    public void analyzeAndStream(UUID userId, BehaviorAnalysisRequest request, SseEmitter emitter) {
        try {
            emit(emitter, EVENT_PROGRESS,
                    new Progress("PREPROCESSING_IMAGE_METADATA", "이미지 위치·시간 정보를 전처리 중입니다."));
            List<PreprocessedImage> items = preprocessor.preprocess(request.images());

            emit(emitter, EVENT_PROGRESS,
                    new Progress("ANALYZING_PREFERENCE", "여행 성향을 분석 중입니다."));
            JsonNode tasteProfileNode = aiClient.analyzeBehavior(AiBehaviorAnalysisRequest.of(userId, items));

            UUID tasteProfileId = persist(userId, tasteProfileNode);

            emit(emitter, EVENT_COMPLETE, ApiResponse.success("행동 데이터 기반 성향 분석 생성 성공",
                    new CompleteData(tasteProfileId.toString(), SourceType.BEHAVIOR.getValue())));
            emitter.complete();
        } catch (BusinessException e) {
            log.warn("Behavior analysis failed: {}", e.getErrorCode());
            completeWithError(emitter, e.getErrorCode());
        } catch (Exception e) {
            log.error("Unexpected error during behavior analysis", e);
            completeWithError(emitter, ErrorCode.INTERNAL_ERROR);
        }
    }

    private UUID persist(UUID userId, JsonNode tasteProfileNode) {
        TasteProfile domain = assembler.toDomain(userId, tasteProfileNode);
        return tasteProfileStore.save(domain);
    }

    private void emit(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(payload, MediaType.APPLICATION_JSON));
    }

    /** 진행 중 오류를 명세 봉투로 담은 {@code error} 이벤트로 전송하고 스트림을 종료한다. */
    private void completeWithError(SseEmitter emitter, ErrorCode code) {
        try {
            emit(emitter, EVENT_ERROR, ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
            emitter.complete();
        } catch (Exception sendFailure) {
            log.debug("Failed to send SSE error event: {}", sendFailure.getMessage());
            emitter.completeWithError(sendFailure);
        }
    }
}
