package com.soma.yeolo.preference.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.preference.service.UserPreferenceService;
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
 * MBTI 저장 API의 응답 계약(API-PREF-1)을 검증한다. 성공 시 {@code data}가 항상 null이고,
 * 잘못된 값이 400으로 나가야 한다는 인수 기준이 배관에서 어긋나기 쉬워 슬라이스로 확인한다.
 */
@WebMvcTest(UserPreferenceController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class UserPreferenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserPreferenceService userPreferenceService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);
        return userId;
    }

    @Test
    void 저장_성공시_200과_data_null로_응답한다() throws Exception {
        UUID userId = authenticate();

        mockMvc.perform(patch("/api/users/me/preferences")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mbti\":\"ENFP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("사용자 MBTI 수정 성공"))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verify(userPreferenceService).updateMbti(eq(userId), any());
    }

    @Test
    void mbti_누락시_400과_명세_메시지로_응답한다() throws Exception {
        authenticate();

        mockMvc.perform(patch("/api/users/me/preferences")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("MBTI 입력값을 확인해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verify(userPreferenceService, never()).updateMbti(any(), any());
    }

    /** 인수 기준: 잘못된 MBTI 값은 400으로 거부된다. */
    @Test
    void 열여섯_유형이_아니면_400과_명세_메시지로_응답한다() throws Exception {
        authenticate();
        doThrow(new BusinessException(ErrorCode.INVALID_MBTI))
                .when(userPreferenceService).updateMbti(any(), any());

        mockMvc.perform(patch("/api/users/me/preferences")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mbti\":\"XXXX\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("MBTI 입력값을 확인해주세요."));
    }

    @Test
    void 미인증_요청은_401로_응답한다() throws Exception {
        mockMvc.perform(patch("/api/users/me/preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mbti\":\"ENFP\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }
}
