package com.soma.yeolo.tasteprofile.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.global.exception.GlobalExceptionHandler;
import com.soma.yeolo.global.sse.SseSliceTestConfig;
import com.soma.yeolo.tasteprofile.service.BehaviorTasteProfileService;
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
 * 사진 분석 동의 게이트(REQ-8 / API-PREF-3의 403)를 검증한다.
 *
 * <p>이 게이트는 {@code produces=text/event-stream} 핸들러 <b>본문 안에서</b> 예외를 던져 막는다.
 * 협상된 Content-Type이 event-stream이라 JSON 봉투가 못 나갈 여지가 있어(전역 핸들러 주석 참고),
 * 실제로 명세대로 403 JSON이 나가는지 슬라이스로 확인해 둔다.
 */
@WebMvcTest(TasteProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class, SseSliceTestConfig.class})
class TasteProfileControllerTest {

    private static final String BODY = """
            {"images":[{"sourceImageId":"img-1","capturedAt":"2026-07-14T10:00:00+09:00",\
            "latitude":33.45,"longitude":126.94}]}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BehaviorTasteProfileService behaviorTasteProfileService;

    @MockitoBean
    private PhotoAnalysisConsentChecker photoAnalysisConsentChecker;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(userId);
        return userId;
    }

    @Test
    void 동의하지_않은_사용자는_스트림을_열지_않고_403_JSON으로_막는다() throws Exception {
        UUID userId = authenticate();
        // 포트의 기본 구현(hasAgreed→403)은 PhotoAnalysisConsentCheckerTest가 검증한다.
        // 여기서는 "차단되면 어떤 응답이 나가는가"만 보므로 차단 자체를 스텁한다.
        doThrow(new BusinessException(ErrorCode.PHOTO_CONSENT_REQUIRED))
                .when(photoAnalysisConsentChecker).requireAgreed(userId);

        // FE·Postman 은 SSE 엔드포인트에 Accept: text/event-stream 으로 보낸다 — 그래도 JSON 봉투가 나가야 한다(#107).
        mockMvc.perform(post("/api/users/me/taste-profile/analysis")
                        .header("Authorization", "Bearer valid-token")
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
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

        mockMvc.perform(post("/api/users/me/taste-profile/analysis")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(request().asyncStarted())
                // 엣지(CloudFront/Cloudflare)가 스트림을 압축하면 15초 하트비트가 버퍼에 갇혀
                // 무활동 타임아웃으로 스트림이 끊긴다. no-transform 이 그걸 막는 유일한 선언이다.
                .andExpect(header().string("Cache-Control", Matchers.containsString("no-transform")))
                .andExpect(header().string("X-Accel-Buffering", "no"));

        verify(behaviorTasteProfileService).analyzeAndStream(any(), any(), any());
    }
}
