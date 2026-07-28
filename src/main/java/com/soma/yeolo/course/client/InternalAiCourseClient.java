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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * AI 코스 생성 내부 API(API-BA-1) 호출 어댑터. 성향 프로필·여행 조건을 SSE로 처리하는 AI 서버에
 * POST하고, {@code complete} 이벤트의 {@code course} 페이로드를 추출해 반환한다.
 * ({@link AiCourseClient}의 유일한 구현체 — 항상 실제 AI 서버를 호출한다.)
 *
 * <p>AI 호출 실패(연결/4xx/5xx)는 사용자에게 {@code AI_COURSE_GENERATION_ERROR}(500)로 노출한다
 * (API-FB-4 §4: 500 — 서버 또는 AI 코스 생성 오류). (docs/architecture.md §5)
 */
@Slf4j
@Component
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
            // 본문은 Jackson 2 ObjectMapper로 직접 직렬화한 바이트로 전송한다. Spring Boot 4의 기본 HTTP
            // 메시지 컨버터는 Jackson 3(tools.jackson)라서, 요청 DTO의 Jackson 2 JsonNode(tasteProfile)를
            // 트리가 아닌 일반 POJO로 취급해 boolean getter(array/object 등)를 필드로 내보낸다. 그 결과 AI
            // 서버가 실제 성향 필드를 받지 못하고 400을 반환한다. 무손실 트리 직렬화를 위해 여기서 직접 굽는다.
            byte[] payload = OBJECT_MAPPER.writeValueAsBytes(request);

            // 바이트를 UTF-8로 명시 디코딩한다. text/event-stream은 charset이 없어 String 변환 시
            // 기본 charset(ISO-8859-1)으로 디코딩되어 한글(코스 제목·추천 이유 등)이 깨질 수 있다.
            byte[] stream = restClient.post()
                    .uri(properties.baseUrl() + COURSES_PATH)
                    .header(INTERNAL_API_KEY_HEADER, properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .body(payload)
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
