package com.soma.yeolo.consent.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.consent.entity.PhotoAnalysisConsent;
import com.soma.yeolo.consent.service.PhotoAnalysisConsentService;
import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import java.time.Instant;
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
 * 동의 저장 API의 응답 계약(API-PREF-2)을 검증한다. 명세의 {@code data.consent} 중첩 구조와
 * 400/401 실패 응답이 배관 실수로 어긋나기 쉬워 컨트롤러 슬라이스로 확인한다.
 */
@WebMvcTest(PhotoAnalysisConsentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class PhotoAnalysisConsentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PhotoAnalysisConsentService photoAnalysisConsentService;

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
    void 동의_저장_성공시_200과_consent_객체를_반환한다() throws Exception {
        UUID userId = authenticate();
        Instant agreedAt = Instant.parse("2026-08-06T09:00:00Z");
        when(photoAnalysisConsentService.save(eq(userId), any()))
                .thenReturn(PhotoAnalysisConsent.record(userId, true, "v1.0", agreedAt));

        mockMvc.perform(post("/api/users/me/consents/photo")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true,\"consentVersion\":\"v1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("사진 데이터 분석 동의 저장 성공"))
                .andExpect(jsonPath("$.data.consent.agreed").value(true))
                .andExpect(jsonPath("$.data.consent.agreedAt").value("2026-08-06T09:00:00Z"))
                .andExpect(jsonPath("$.data.consent.consentVersion").value("v1.0"));
    }

    @Test
    void 철회도_같은_API로_받아_agreed_false를_반환한다() throws Exception {
        UUID userId = authenticate();
        when(photoAnalysisConsentService.save(eq(userId), any()))
                .thenReturn(PhotoAnalysisConsent.record(
                        userId, false, "v1.0", Instant.parse("2026-08-06T09:00:00Z")));

        mockMvc.perform(post("/api/users/me/consents/photo")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":false,\"consentVersion\":\"v1.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consent.agreed").value(false));
    }

    @Test
    void agreed_누락시_400과_명세_메시지로_응답한다() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/users/me/consents/photo")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consentVersion\":\"v1.0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사진 데이터 분석 동의 입력값을 확인해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void consentVersion_누락시_400과_명세_메시지로_응답한다() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/users/me/consents/photo")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사진 데이터 분석 동의 입력값을 확인해주세요."));
    }

    @Test
    void 미인증_요청은_401로_응답한다() throws Exception {
        mockMvc.perform(post("/api/users/me/consents/photo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"agreed\":true,\"consentVersion\":\"v1.0\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }
}
