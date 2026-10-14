package com.soma.yeolo.course.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.course.service.CourseCreationService;
import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.GlobalExceptionHandler;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.global.sse.SseSliceTestConfig;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 코스 생성(API-COURSE-1)의 요청 경계. SSE 엔드포인트라 클라이언트가 {@code Accept: text/event-stream}
 * 으로 보내는데, 핸들러 전에 나는 검증 400 도 명세의 JSON 봉투로 나가야 한다(#107 — 전에는 Accept 와
 * JSON 을 협상하지 못해 본문 없는 400 이 나갔다).
 */
@WebMvcTest(CourseController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class, SseSliceTestConfig.class})
class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourseCreationService courseCreationService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    private void authenticate() {
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(UUID.randomUUID());
    }

    /** 옛 클라이언트가 {@code budgetType}만 보내면 {@code budget} 누락 400 — SSE Accept 로도 JSON 봉투가 나간다. */
    @Test
    void SSE_Accept로_보낸_검증_실패도_명세의_JSON_봉투로_400이다() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer valid-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationCountry":"일본","destinationCity":"오사카","startDate":"2026-11-01",
                                 "totalDays":3,"budgetType":"moderate"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("여행 조건 입력값이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
        verify(courseCreationService, never()).createAndStream(any(), any(), any());
    }

    /** 깨진 JSON 도 같은 경로 — 본문 없는 400 이 아니라 봉투가 나간다. */
    @Test
    void SSE_Accept로_보낸_깨진_JSON도_JSON_봉투로_400이다() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer valid-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{budget: 500000"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    /** SSE 엔드포인트에 JSON 만 받겠다고 보내면 500 이 아니라 406 봉투다 — 요청 쪽 오류다. */
    @Test
    void Accept가_맞지_않으면_406_봉투다() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/courses")
                        .header("Authorization", "Bearer valid-token")
                        .accept(MediaType.APPLICATION_JSON)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"destinationCountry":"일본","destinationCity":"오사카","startDate":"2026-11-01",
                                 "totalDays":3,"budget":500000}
                                """))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.message").value("지원하지 않는 응답 형식입니다."));
        verify(courseCreationService, never()).createAndStream(any(), any(), any());
    }

    @Test
    void 인증_없이_보내면_401_봉투다() throws Exception {
        mockMvc.perform(post("/api/courses")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
