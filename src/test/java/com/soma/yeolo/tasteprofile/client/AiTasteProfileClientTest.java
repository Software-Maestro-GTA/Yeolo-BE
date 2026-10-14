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
import com.soma.yeolo.tasteprofile.client.dto.AiTasteAnalysis;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiTasteProfileClientTest {

    private static final String URL = "http://ai/internal/ai/taste-profile/analysis";

    private MockRestServiceServer server;
    private AiTasteProfileClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        client = new AiTasteProfileClient(restClient,
                new AiClientProperties("http://ai", "internal-key", 3_000L, 240_000L));
    }

    private AiBehaviorAnalysisRequest request() {
        return new AiBehaviorAnalysisRequest("11111111-1111-1111-1111-111111111111", "ko", List.of());
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
                // 2026-10 개정: 선호 언어를 헤더에도 싣는다(API-AI-1).
                .andExpect(header("Accept-Language", "ko"))
                .andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        AiTasteAnalysis analysis = client.analyzeBehavior(request());
        JsonNode profile = analysis.tasteProfile();

        assertThat(profile.get("sourceType").asText()).isEqualTo("behavior");
        assertThat(profile.get("travelPaceDensity").asText()).isEqualTo("balanced");
        // 개정 전 AI 응답(summary 없음)도 지표만으로 분석은 성공한다 — 요약은 null.
        assertThat(analysis.summary()).isNull();
        server.verify();
    }

    /** 2026-10 개정: complete 에 summary(intro·hashtags·types·recommendCity)가 함께 온다. 원본 트리 그대로 꺼낸다. */
    @Test
    void complete_이벤트의_summary를_함께_추출한다() {
        String sse = """
                event: progress
                data: {"step":"GENERATING_SUMMARY","message":"요약 중"}

                event: complete
                data: {"tasteProfile":{"sourceType":"behavior"},"summary":{"intro":"느긋한 미식가","hashtags":["#미식"],"types":[{"typeCode":"PACE","typeName":"여행 속도","lowName":"느긋","highName":"빡빡","score":2.5}],"recommendCity":{"countryName":"일본","cityName":"오사카","reason":"먹거리"}}}

                """;
        // 한글 본문은 charset 이 없으면 ISO-8859-1 로 읽혀 깨진다 — 실제 AI 는 utf-8 을 명시한다.
        server.expect(requestTo(URL)).andRespond(withSuccess(sse,
                new MediaType(MediaType.TEXT_EVENT_STREAM, StandardCharsets.UTF_8)));

        AiTasteAnalysis analysis = client.analyzeBehavior(request());

        assertThat(analysis.summary().get("intro").asText()).isEqualTo("느긋한 미식가");
        assertThat(analysis.summary().get("recommendCity").get("cityName").asText()).isEqualTo("오사카");
        assertThat(analysis.summary().get("types").get(0).get("score").asDouble()).isEqualTo(2.5);
    }

    /** summary 자리에 객체가 아닌 값이 오면 요약 없음으로 다룬다(분석 자체는 성공). */
    @Test
    void summary가_객체가_아니면_null로_둔다() {
        String sse = "event: complete\ndata: {\"tasteProfile\":{\"sourceType\":\"behavior\"},\"summary\":\"n/a\"}\n\n";
        server.expect(requestTo(URL)).andRespond(withSuccess(sse, MediaType.TEXT_EVENT_STREAM));

        assertThat(client.analyzeBehavior(request()).summary()).isNull();
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
