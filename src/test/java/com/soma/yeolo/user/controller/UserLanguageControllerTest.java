package com.soma.yeolo.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.exception.GlobalExceptionHandler;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.user.service.UserLanguageService;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** API-USER-3/4 — 경로·봉투·메시지·에러 코드를 명세대로 고정한다. 인증 필요. */
@WebMvcTest(UserLanguageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        GlobalExceptionHandler.class})
class UserLanguageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserLanguageService userLanguageService;

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
    void 조회는_저장된_언어를_명세_봉투로_응답한다() throws Exception {
        UUID userId = authenticate();
        when(userLanguageService.findLanguage(userId)).thenReturn("ko");

        mockMvc.perform(get("/api/users/me/language").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("언어 설정 조회 성공"))
                .andExpect(jsonPath("$.data.language").value("ko"));
    }

    @Test
    void 조회는_인증이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/users/me/language"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }

    @Test
    void 변경은_저장된_코드를_명세_봉투로_응답한다() throws Exception {
        UUID userId = authenticate();
        when(userLanguageService.updateLanguage(userId, "ko")).thenReturn("ko");

        mockMvc.perform(post("/api/users/me/language")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ko\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("언어 설정 변경 성공"))
                .andExpect(jsonPath("$.data.language").value("ko"));
    }

    @Test
    void language가_누락되거나_비면_400과_명세_문구다() throws Exception {
        authenticate();

        for (String body : new String[]{"{}", "{\"language\":null}", "{\"language\":\"\"}", "{\"language\":\"  \"}"}) {
            mockMvc.perform(post("/api/users/me/language")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("지원하는 언어 코드를 입력해주세요."))
                    .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
        }
        verify(userLanguageService, never()).updateLanguage(any(), anyString());
    }

    @Test
    void 지원하지_않는_코드는_400과_명세_문구다() throws Exception {
        UUID userId = authenticate();
        when(userLanguageService.updateLanguage(userId, "ja"))
                .thenThrow(new BusinessException(ErrorCode.UNSUPPORTED_LANGUAGE));

        mockMvc.perform(post("/api/users/me/language")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"ja\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("지원하는 언어 코드를 입력해주세요."));
    }

    /** 문자열이 아닌 타입은 검증 단계에서 명세 문구로 거절한다 — Jackson 이 123 을 "123" 으로 바꿔 넣는 것을 막는다. */
    @Test
    void 문자열이_아닌_타입이면_400과_명세_문구다() throws Exception {
        authenticate();

        for (String body : new String[]{"{\"language\": 123}", "{\"language\": [\"ko\"]}", "{\"language\": {\"code\":\"ko\"}}"}) {
            mockMvc.perform(post("/api/users/me/language")
                            .header("Authorization", "Bearer valid-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value("지원하는 언어 코드를 입력해주세요."))
                    .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
        }
        verify(userLanguageService, never()).updateLanguage(any(), anyString());
    }

    /**
     * 깨진 JSON 은 본문 파싱 단계에서 전역 핸들러가 400 으로 막는다. 문구는 명세 예시가 아니라 공통 문구다
     * (엔드포인트를 알기 전에 실패하므로) — docs/spec-index.md 에 기록한 의도된 차이라 여기서 고정한다.
     */
    @Test
    void 깨진_JSON이면_공통_400_문구다() throws Exception {
        authenticate();

        mockMvc.perform(post("/api/users/me/language")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{language: ko"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청이 유효하지 않습니다."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
        verify(userLanguageService, never()).updateLanguage(any(), anyString());
    }
}
