package com.soma.yeolo.user.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.user.service.UserWithdrawalService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserWithdrawalService userWithdrawalService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void 회원탈퇴_성공시_200과_명세_봉투로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"더 이상 사용하지 않아요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("회원탈퇴 성공"))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));

        verify(userWithdrawalService).withdraw(userId);
    }

    @Test
    void 회원탈퇴_바디_없이도_200으로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("회원탈퇴 성공"));
    }

    @Test
    void 미인증_회원탈퇴는_401과_명세_봉투로_응답한다() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void 존재하지_않는_사용자_탈퇴는_404와_명세_메시지로_응답한다() throws Exception {
        UUID userId = UUID.randomUUID();
        org.mockito.Mockito.when(jwtTokenProvider.parseUserId("valid-token")).thenReturn(userId);
        doThrow(new BusinessException(ErrorCode.USER_NOT_FOUND))
                .when(userWithdrawalService).withdraw(userId);

        mockMvc.perform(delete("/api/users/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }
}
