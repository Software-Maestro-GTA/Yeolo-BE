package com.soma.yeolo.course.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.client.dto.AiCourseGenerationRequest;
import com.soma.yeolo.global.client.AiClientProperties;
import com.soma.yeolo.global.client.SseStreamParser;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 코스 생성 내부 API(API-BA-1) 호출 어댑터. 성향 프로필·여행 조건을 SSE로 처리하는 AI 서버에
 * POST하고, {@code complete} 이벤트의 {@code course} 페이로드를 추출해 반환한다.
 * ({@code ai.course.provider=internal}일 때 활성화, 기본은 {@link StubAiCourseClient})
 *
 * <p>AI 호출 실패(연결/4xx/5xx)는 사용자에게 {@code AI_COURSE_GENERATION_ERROR}(500)로 노출한다
 * (API-FB-4 §4: 500 — 서버 또는 AI 코스 생성 오류). (docs/architecture.md §5)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.course.provider", havingValue = "internal")
public class InternalAiCourseClient implements AiCourseClient {

    private static final String COURSES_PATH = "/internal/ai/courses";
    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestClient restClient;
    private final AiClientProperties properties;

    public InternalAiCourseClient(@Qualifier("aiRestClient") RestClient restClient,
                                  AiClientProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public JsonNode generateCourse(AiCourseGenerationRequest request) {
        try {
            // 바이트를 UTF-8로 명시 디코딩한다. text/event-stream은 charset이 없어 String 변환 시
            // 기본 charset(ISO-8859-1)으로 디코딩되어 한글(코스 제목·추천 이유 등)이 깨질 수 있다.
            byte[] stream = restClient.post()
                    .uri(properties.baseUrl() + COURSES_PATH)
                    .header(INTERNAL_API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(byte[].class);
            return extractCourse(stream == null ? null : new String(stream, StandardCharsets.UTF_8));
        } catch (RestClientResponseException e) {
            log.error("AI course generation rejected: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR, e);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI course generation call failed (connectivity)", e);
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR, e);
        }
    }

    /** SSE 스트림 텍스트에서 {@code complete} 이벤트의 {@code data.course}를 추출한다. */
    private JsonNode extractCourse(String stream) {
        String data = SseStreamParser.dataOfEvent(stream, "complete");
        if (data == null) {
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(data);
            JsonNode course = root.get("course");
            if (course == null || course.isNull()) {
                throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR);
            }
            return course;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse AI complete payload", e);
            throw new BusinessException(ErrorCode.AI_COURSE_GENERATION_ERROR, e);
        }
    }
}
