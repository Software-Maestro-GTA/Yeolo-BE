package com.soma.yeolo.tasteprofile.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.global.sse.SseHeartbeat;
import com.soma.yeolo.global.sse.SseStream;
import com.soma.yeolo.tasteprofile.client.AiTasteProfileClient;
import com.soma.yeolo.tasteprofile.client.dto.AiBehaviorAnalysisRequest;
import com.soma.yeolo.tasteprofile.domain.PreprocessedImage;
import com.soma.yeolo.tasteprofile.domain.TasteProfile;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisEvents.CompleteData;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisEvents.Progress;
import com.soma.yeolo.tasteprofile.dto.BehaviorAnalysisRequest;
import com.soma.yeolo.tasteprofile.service.port.TasteProfileRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 이미지 메타데이터 기반 취향 분석 오케스트레이션 (API-PREF-3 / FUN-4).
 *
 * <p>전처리(Reverse Geocode + 시간 맥락) → AI 분석 호출(API-AI-1) → 결과 저장(DOM-1) 순으로
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
    private final TasteProfileRepository tasteProfileRepository;
    private final SseHeartbeat heartbeat;

    /** 전처리 → AI 분석 → 저장 파이프라인을 실행하고 진행 상황을 SSE로 중계한다. */
    public void analyzeAndStream(UUID userId, BehaviorAnalysisRequest request, SseEmitter emitter) {
        SseStream stream = new SseStream(emitter);
        // 전처리(이미지당 Reverse Geocode, 호출 간 최소 간격)와 AI 분석(수십 초) 모두 응답 바이트가
        // 나가지 않는 구간이다. 프록시가 연결을 idle로 보고 끊지 않도록 전 구간 heartbeat를 유지한다.
        try (SseHeartbeat.Handle beat = heartbeat.start(stream)) {
            stream.send(EVENT_PROGRESS,
                    new Progress("PREPROCESSING_IMAGE_METADATA", "이미지 위치·시간 정보를 전처리 중입니다."));
            List<PreprocessedImage> items = preprocessor.preprocess(request.images());

            stream.send(EVENT_PROGRESS,
                    new Progress("ANALYZING_PREFERENCE", "여행 성향을 분석 중입니다."));
            JsonNode tasteProfileNode = aiClient.analyzeBehavior(AiBehaviorAnalysisRequest.of(userId, items));

            // 클라이언트가 이미 떠났어도 분석은 끝났으므로 저장은 진행한다(재요청 시 재분석 불필요).
            UUID tasteProfileId = persist(userId, tasteProfileNode);

            if (!stream.send(EVENT_COMPLETE, ApiResponse.success("행동 데이터 기반 취향 분석 생성 성공",
                    new CompleteData(tasteProfileId.toString())))) {
                log.warn("취향 분석 완료 이벤트를 전달하지 못했다(클라이언트 이탈). userId={}, tasteProfileId={}",
                        userId, tasteProfileId);
            }
            stream.complete();
        } catch (BusinessException e) {
            log.warn("Behavior analysis failed: {}", e.getErrorCode());
            completeWithError(stream, e.getErrorCode());
        } catch (Exception e) {
            log.error("Unexpected error during behavior analysis", e);
            completeWithError(stream, ErrorCode.INTERNAL_ERROR);
        }
    }

    private UUID persist(UUID userId, JsonNode tasteProfileNode) {
        TasteProfile domain = assembler.toDomain(userId, tasteProfileNode);
        return tasteProfileRepository.save(domain);
    }

    /**
     * 진행 중 오류를 명세 봉투로 담은 {@code error} 이벤트로 전송하고 스트림을 종료한다.
     * 이미 끊긴 스트림이면 {@link SseStream}이 조용히 무시한다 — 여기서 예외를 다시 던지면
     * 서블릿으로 재디스패치되어 전역 핸들러가 500을 찍는다.
     */
    private void completeWithError(SseStream stream, ErrorCode code) {
        stream.send(EVENT_ERROR, ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
        stream.complete();
    }
}
