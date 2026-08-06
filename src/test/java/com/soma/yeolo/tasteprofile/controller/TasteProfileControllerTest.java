package com.soma.yeolo.tasteprofile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.consent.service.PhotoAnalysisConsentChecker;
import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.sse.SseProperties;
import com.soma.yeolo.tasteprofile.service.BehaviorTasteProfileService;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 사진 분석 동의 게이트(REQ-8 / API-PREF-3의 403)를 검증한다.
 *
 * <p>이 게이트는 {@code produces=text/event-stream} 핸들러 <b>본문 안에서</b> 예외를 던져 막는다.
 * 협상된 Content-Type이 event-stream이라 JSON 봉투가 못 나갈 여지가 있어(전역 핸들러 주석 참고),
 * 실제로 명세대로 403 JSON이 나가는지 슬라이스로 확인해 둔다.
 */
@WebMvcTest(TasteProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        TasteProfileControllerTest.SseTestBeans.class})
class TasteProfileControllerTest {

    @TestConfiguration
    static class SseTestBeans {

        @Bean
        SseProperties sseProperties() {
            return new SseProperties(600_000L);
        }

        /** 워커를 인라인 실행해 파이프라인 시작 여부를 경합 없이 검증한다. */
        @Bean(name = "sseTaskExecutor")
        AsyncTaskExecutor sseTaskExecutor() {
            return new TaskExecutorAdapter(Runnable::run);
        }
    }

    private static final String BODY = """
            {"images":[{"sourceImageId":"img-1","capturedAt":"2026-07-14T10:00:00+09:00",\
            "latitude":33.45,"longitude":126.94,"timezone":"Asia/Seoul"}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BehaviorTasteProfileService behaviorTasteProfileService;

    @MockitoBean
    private PhotoAnalysisConsentChecker photoAnalysisConsentChecker;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);
        return userId;
    }

    @Test
    void 동의하지_않은_사용자는_스트림을_열지_않고_403_JSON으로_막는다() throws Exception {
        UUID userId = authenticate();
        // 포트의 기본 구현(hasAgreed→403)은 PhotoAnalysisConsentCheckerTest가 검증한다.
        // 여기서는 "차단되면 어떤 응답이 나가는가"만 보므로 차단 자체를 스텁한다.
        doThrow(new BusinessException(ErrorCode.PHOTO_CONSENT_REQUIRED))
                .when(photoAnalysisConsentChecker).requireAgreed(userId);

        mockMvc.perform(post("/api/taste-profile/behavior")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("개인정보 수집·활용 동의가 필요합니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        // 분석 파이프라인 자체가 시작되지 않아야 한다 (REQ-8: 동의 없으면 분석을 진행하지 않는다).
        verify(behaviorTasteProfileService, never()).analyzeAndStream(any(), any(), any());
    }

    @Test
    void 동의한_사용자는_분석_스트림을_시작한다() throws Exception {
        authenticate();
        // 동의한 경우 requireAgreed는 아무것도 하지 않는다(mock 기본 동작과 동일).

        mockMvc.perform(post("/api/taste-profile/behavior")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(request().asyncStarted());

        verify(behaviorTasteProfileService).analyzeAndStream(any(), any(), any());
    }
}
