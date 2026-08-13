package com.soma.yeolo.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.soma.yeolo.global.config.SecurityConfig;
import com.soma.yeolo.global.exception.BusinessException;
import com.soma.yeolo.global.exception.ErrorCode;
import com.soma.yeolo.global.security.JwtAuthenticationFilter;
import com.soma.yeolo.global.security.JwtTokenProvider;
import com.soma.yeolo.global.security.RestAuthenticationEntryPoint;
import com.soma.yeolo.global.security.WithdrawnUserChecker;
import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.dto.UserProfileUpdateRequest;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.service.UserProfileService;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * 프로필 수정 API의 응답 계약(API-USER-1)을 검증한다.
 *
 * <p>multipart 폼 필드를 record DTO로 바인딩하는 배관은 실수하기 쉽고(파트 이름 불일치는 조용히
 * null이 된다), 명세의 {@code data.user} 중첩 구조와 400/409/415 분기도 여기서만 드러난다.
 */
@WebMvcTest(UserProfileController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class UserProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserProfileService userProfileService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private WithdrawnUserChecker withdrawnUserChecker;

    private UUID authenticate() {
        UUID userId = UUID.randomUUID();
        when(jwtTokenProvider.parseAccessTokenUserId("valid-token")).thenReturn(userId);
        return userId;
    }

    /** id는 DB가 발급하므로(@GeneratedValue) 응답 조립에 필요한 값만 채워 넣는다. */
    private User savedUser(UUID userId, String email, String displayName, String imageUrl) {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1", email, displayName, imageUrl);
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    /** PATCH + multipart. {@code multipart()}는 기본이 POST라 메서드를 바꿔 준다. */
    private MockMultipartHttpServletRequestBuilder patchMultipart() {
        return (MockMultipartHttpServletRequestBuilder)
                multipart(HttpMethod.PATCH, "/api/users/me/profile");
    }

    @Test
    void 수정_성공시_200과_user_객체를_반환한다() throws Exception {
        UUID userId = authenticate();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenReturn(savedUser(userId, "new@gmail.com", "새이름", "https://cdn.test/a.png"));

        mockMvc.perform(patchMultipart()
                        .param("email", "new@gmail.com")
                        .param("displayName", "새이름")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.message").value("사용자 프로필 수정 성공"))
                .andExpect(jsonPath("$.data.user.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.user.provider").value("google"))
                .andExpect(jsonPath("$.data.user.email").value("new@gmail.com"))
                .andExpect(jsonPath("$.data.user.displayName").value("새이름"))
                .andExpect(jsonPath("$.data.user.profileImageUrl").value("https://cdn.test/a.png"))
                .andExpect(jsonPath("$.data.user.status").value("active"))
                .andExpect(jsonPath("$.data.user.lastLoginAt").isNotEmpty());
    }

    /** DOM-1: email·displayName·profileImageUrl 은 null 로 내려갈 수 있다. */
    @Test
    void 값이_없는_프로필_항목은_null로_내려간다() throws Exception {
        UUID userId = authenticate();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenReturn(savedUser(userId, null, null, null));

        mockMvc.perform(patchMultipart()
                        .param("displayName", "새이름")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.user.displayName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.user.profileImageUrl").value(Matchers.nullValue()));
    }

    @Test
    void 폼_필드와_파일_파트가_모두_DTO로_바인딩된다() throws Exception {
        UUID userId = authenticate();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenReturn(savedUser(userId, "new@gmail.com", "새이름", "https://cdn.test/a.png"));
        MockMultipartFile image = new MockMultipartFile(
                "profileImage", "me.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3});

        mockMvc.perform(patchMultipart()
                        .file(image)
                        .param("displayName", "새이름")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        ArgumentCaptor<UserProfileUpdateRequest> captured =
                ArgumentCaptor.forClass(UserProfileUpdateRequest.class);
        verify(userProfileService).updateProfile(eq(userId), captured.capture());
        assertThat(captured.getValue().displayName()).isEqualTo("새이름");
        assertThat(captured.getValue().profileImage()).isNotNull();
    }

    /**
     * 명세 개정으로 요청에서 {@code email}이 빠졌다. 보내더라도 바인딩되는 필드가 없으므로 무시된다
     * — 400이 아니라 "그냥 반영되지 않는" 것이 맞다(모르는 파트는 거절하지 않는다).
     */
    @Test
    void email_파트를_보내도_무시한다() throws Exception {
        UUID userId = authenticate();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenReturn(savedUser(userId, "oauth@gmail.com", "새이름", null));

        mockMvc.perform(patchMultipart()
                        .param("email", "new@gmail.com")
                        .param("displayName", "새이름")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value("oauth@gmail.com"));
    }

    @Test
    void 표시_이름이_너무_길면_400과_명세_메시지로_응답한다() throws Exception {
        authenticate();

        mockMvc.perform(patchMultipart()
                        .param("displayName", "가".repeat(51))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("사용자 프로필 입력값을 확인해주세요."))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));

        verify(userProfileService, never()).updateProfile(any(), any());
    }

    @Test
    void 지원하지_않는_이미지_형식이면_415로_응답한다() throws Exception {
        UUID userId = authenticate();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenThrow(new BusinessException(ErrorCode.UNSUPPORTED_PROFILE_IMAGE_TYPE));

        mockMvc.perform(patchMultipart()
                        .file(new MockMultipartFile("profileImage", "a.png",
                                MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.message").value("지원하지 않는 이미지 형식입니다."));
    }

    @Test
    void 프로필_이미지_용량_초과는_413으로_응답한다() throws Exception {
        UUID userId = authenticate();
        when(userProfileService.updateProfile(eq(userId), any()))
                .thenThrow(new BusinessException(ErrorCode.PROFILE_IMAGE_TOO_LARGE));

        mockMvc.perform(patchMultipart()
                        .file(new MockMultipartFile("profileImage", "big.png",
                                MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2, 3}))
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.status").value(413))
                .andExpect(jsonPath("$.message").value("프로필 이미지 용량이 너무 큽니다."));
    }

    /** multipart 가 아닌 요청은 catch-all 500이 아니라 명세의 415여야 한다. */
    @Test
    void JSON으로_보내면_415로_응답한다() throws Exception {
        authenticate();

        mockMvc.perform(patch("/api/users/me/profile")
                        .header("Authorization", "Bearer valid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"새이름\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void 미인증_요청은_401로_응답한다() throws Exception {
        mockMvc.perform(patchMultipart().param("displayName", "새이름"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.data").value(Matchers.nullValue()));
    }
}
