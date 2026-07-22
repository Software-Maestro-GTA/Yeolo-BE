package com.soma.yeolo.tasteprofile.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.soma.yeolo.global.client.AiClientProperties;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.tasteprofile.client.dto.AiBehaviorAnalysisRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiTasteProfileClientTest {

    private static final String URL = "http://ai/internal/ai/taste-profile/behavior";

    private MockRestServiceServer server;
    private AiTasteProfileClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new AiTasteProfileClient(restClient,
                new AiClientProperties("http://ai", "internal-key"));
    }

    private AiBehaviorAnalysisRequest request() {
        return new AiBehaviorAnalysisRequest("11111111-1111-1111-1111-111111111111", List.of());
    }

    @Test
    void complete_이벤트의_tasteProfile을_추출한다() {
        String sse = """
                event: progress
                data: {"step":"ANALYZING_PREFERENCE","message":"분석 중"}

                event: complete
                data: {"tasteProfile":{"sourceType":"behavior","travelPaceDensity":"balanced"}}

                """;
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Api-Key", "internal-key"))
                .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        JsonNode profile = client.analyzeBehavior(request());

        assertThat(profile.get("sourceType").asText()).isEqualTo("behavior");
        assertThat(profile.get("travelPaceDensity").asText()).isEqualTo("balanced");
        server.verify();
    }

    @Test
    void AI가_5xx면_AI_ANALYSIS_ERROR로_변환한다() {
        server.expect(requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.analyzeBehavior(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_ANALYSIS_ERROR);
    }

    @Test
    void complete_이벤트가_없으면_AI_ANALYSIS_ERROR로_변환한다() {
        String sse = "event: progress\ndata: {\"step\":\"ANALYZING_PREFERENCE\"}\n\n";
        server.expect(requestTo(URL)).andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        assertThatThrownBy(() -> client.analyzeBehavior(request()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_ANALYSIS_ERROR);
    }
}
