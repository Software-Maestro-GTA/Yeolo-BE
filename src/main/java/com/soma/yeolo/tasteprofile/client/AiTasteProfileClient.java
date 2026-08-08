package com.soma.yeolo.tasteprofile.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.client.AiClientProperties;
import com.soma.yeolo.global.client.SseStreamParser;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.client.dto.AiBehaviorAnalysisRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 취향 분석 내부 API(API-AI-1) 호출 어댑터. 전처리 메타데이터를 SSE로 전송받는 AI 서버에
 * POST하고, {@code complete} 이벤트의 {@code tasteProfile} 페이로드를 추출해 반환한다.
 *
 * <p>AI 호출 실패(연결/4xx/5xx)는 사용자에게 {@code AI_ANALYSIS_ERROR}(500)로 노출한다
 * (API-PREF-3 §3: 500 — 서버 또는 AI 분석 오류). (docs/architecture.md §5)
 */
@Slf4j
@Component
public class AiTasteProfileClient {

    private static final String ANALYSIS_PATH = "/internal/ai/taste-profile/analysis";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final AiClientProperties properties;

    public AiTasteProfileClient(@Qualifier("aiRestClient") RestClient restClient,
                                AiClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 전처리 메타데이터로 AI 성향 분석을 요청하고, 정규화된 성향 프로필({@code tasteProfile})을 반환한다.
     *
     * @return AI가 산출한 {@code tasteProfile} JSON 노드 (sourceType, travelPurpose 등 포함)
     */
    public JsonNode analyzeBehavior(AiBehaviorAnalysisRequest request) {
        try {
            String stream = restClient.post()
                    .uri(properties.baseUrl() + ANALYSIS_PATH)
                    .header(INTERNAL_API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return extractTasteProfile(stream);
        } catch (RestClientResponseException e) {
            log.error("AI behavior analysis rejected: {} {} | request={}",
                    e.getStatusCode(), e.getResponseBodyAsString(), toJsonForLog(request));
            throw new BusinessException(ErrorCode.AI_ANALYSIS_ERROR, e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI behavior analysis call failed (connectivity)", e);
            throw new BusinessException(ErrorCode.AI_ANALYSIS_ERROR, e);
        }
    }

    /**
     * 진단용 요청 본문 직렬화. AI 400(전처리 메타데이터 부족/형식 오류) 원인 추적을 위해 실제 전송한
     * payload를 로그에 남긴다. 직렬화 자체가 실패해도 원래 예외 흐름을 가리지 않도록 삼켜서 표식만 반환한다.
     */
    private String toJsonForLog(AiBehaviorAnalysisRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            return "<unserializable request: " + e.getMessage() + ">";
        }
    }

    /** SSE 스트림 텍스트에서 {@code complete} 이벤트의 {@code data.tasteProfile}를 추출한다. */
    private JsonNode extractTasteProfile(String stream) {
        String data = SseStreamParser.dataOfEvent(stream, "complete");
        if (data == null) {
            throw new BusinessException(ErrorCode.AI_ANALYSIS_ERROR);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(data);
            JsonNode tasteProfile = root.get("tasteProfile");
            if (tasteProfile == null || tasteProfile.isNull()) {
                throw new BusinessException(ErrorCode.AI_ANALYSIS_ERROR);
            }
            return tasteProfile;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI complete payload", e);
            throw new BusinessException(ErrorCode.AI_ANALYSIS_ERROR, e);
        }
    }
}
