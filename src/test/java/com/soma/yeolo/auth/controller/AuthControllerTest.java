package com.soma.yeolo.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.auth.dto.AppleLoginResponse;
import com.soma.yeolo.auth.dto.GoogleLoginResponse;
import com.soma.yeolo.auth.dto.GoogleLoginResponse.UserSummary;
import com.soma.yeolo.auth.service.AuthService;
import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 구글_로그인_성공시_200과_명세_봉투로_응답한다() throws Exception {
        GoogleLoginResponse response = new GoogleLoginResponse(
                new UserSummary("550e8400-e29b-41d4-a716-446655440000", "google", "u@gmail.com",
                        "홍길동", "http://img", "active", "2026-07-16T00:00:00Z"),
                true, "access-token", "refresh-token");
        when(authService.loginWithGoogle(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"redirectUri\":\"http://localhost/callback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("로그인 성공"))
                .andExpect(jsonPath("$.data.user.userId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.data.user.provider").value("google"))
                .andExpect(jsonPath("$.data.user.displayName").value("홍길동"))
                .andExpect(jsonPath("$.data.user.profileImageUrl").value("http://img"))
                .andExpect(jsonPath("$.data.user.status").value("active"))
                .andExpect(jsonPath("$.data.user.lastLoginAt").value("2026-07-16T00:00:00Z"))
                .andExpect(jsonPath("$.data.doOnboarding").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void 인가코드_누락시_400과_명세_메시지로_응답한다() throws Exception {
        mockMvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redirectUri\":\"http://localhost/callback\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("인가 코드가 유효하지 않습니다."))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 애플_로그인_성공시_200과_명세_봉투로_응답한다() throws Exception {
        AppleLoginResponse response = new AppleLoginResponse(
                new AppleLoginResponse.UserSummary("550e8400-e29b-41d4-a716-446655440000", "apple",
                        "u@privaterelay.appleid.com", null, null, "active", "2026-07-16T00:00:00Z"),
                true, "access-token", "refresh-token");
        when(authService.loginWithApple(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"redirectUri\":\"http://localhost/callback\","
                                + "\"idToken\":\"id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("로그인 성공"))
                .andExpect(jsonPath("$.data.user.userId").value("550e8400-e29b-41d4-a716-446655440000"))
                .andExpect(jsonPath("$.data.user.provider").value("apple"))
                .andExpect(jsonPath("$.data.user.email").value("u@privaterelay.appleid.com"))
                .andExpect(jsonPath("$.data.user.displayName").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.user.profileImageUrl").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.user.status").value("active"))
                .andExpect(jsonPath("$.data.doOnboarding").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-token"));
    }

    @Test
    void 애플_로그인_바디에서_idToken은_생략할_수_있다() throws Exception {
        AppleLoginResponse response = new AppleLoginResponse(
                new AppleLoginResponse.UserSummary("550e8400-e29b-41d4-a716-446655440000", "apple",
                        null, null, null, "active", "2026-07-16T00:00:00Z"),
                true, "access-token", "refresh-token");
        when(authService.loginWithApple(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"auth-code\",\"redirectUri\":\"http://localhost/callback\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.user.provider").value("apple"));
    }

    @Test
    void 애플_인가코드_누락시_400과_명세_메시지로_응답한다() throws Exception {
        mockMvc.perform(post("/api/auth/apple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"redirectUri\":\"http://localhost/callback\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("인가 코드가 유효하지 않습니다."))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 로그아웃_성공시_200과_명세_봉투로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"refresh-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("로그아웃 성공"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 로그아웃_바디_없이도_200으로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("로그아웃 성공"));
    }

    @Test
    void 미인증_로그아웃은_401과_명세_봉투로_응답한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
}
