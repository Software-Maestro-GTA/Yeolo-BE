package com.soma.yeolo.course.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.course.client.dto.AiCourseGenerationRequest;
import com.soma.yeolo.course.domain.BudgetType;
import com.soma.yeolo.course.domain.TripCondition;
import com.soma.yeolo.global.client.AiClientProperties;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class InternalAiCourseClientTest {

    private static final String URL = "http://ai/internal/ai/courses";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockRestServiceServer server;
    private InternalAiCourseClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new InternalAiCourseClient(restClient,
                new AiClientProperties("http://ai", "internal-key"));
    }

    private AiCourseGenerationRequest request() throws Exception {
        JsonNode tasteProfile = MAPPER.readTree("{\"sourceType\":\"behavior\"}");
        TripCondition condition = new TripCondition("대한민국", "제주",
                LocalDate.of(2026, 8, 1), 3, BudgetType.COST_EFFECTIVE);
        return AiCourseGenerationRequest.of(UUID.randomUUID(), tasteProfile, condition);
    }

    @Test
    void complete_이벤트의_course를_추출한다() throws Exception {
        String sse = """
                event: progress
                data: {"step":"GENERATING_ROUTE","message":"구성 중"}

                event: complete
                data: {"course":{"title":"2박 3일 제주 힐링 코스","totalCost":480000}}

                """;
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-key"))
                .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        JsonNode course = client.generateCourse(request());

        assertThat(course.get("title").asText()).isEqualTo("2박 3일 제주 힐링 코스");
        assertThat(course.get("totalCost").asInt()).isEqualTo(480000);
        server.verify();
    }

    @Test
    void AI가_5xx면_AI_COURSE_GENERATION_ERROR로_변환한다() throws Exception {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.generateCourse(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_COURSE_GENERATION_ERROR);
    }

    @Test
    void complete_이벤트가_없으면_AI_COURSE_GENERATION_ERROR로_변환한다() throws Exception {
        String sse = "event: progress\ndata: {\"step\":\"GENERATING_ROUTE\"}\n\n";
        server.expect(requestTo(URL)).andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        assertThatThrownBy(() -> client.generateCourse(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_COURSE_GENERATION_ERROR);
    }
}
