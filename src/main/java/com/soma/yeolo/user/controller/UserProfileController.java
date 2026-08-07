package com.soma.yeolo.user.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.user.dto.UserProfileResponse;
import com.soma.yeolo.user.dto.UserProfileUpdateRequest;
import com.soma.yeolo.user.service.UserProfileService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 프로필 등록/수정 API (API-USER-1).
 */
@RestController
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /**
     * 프로필 등록/수정 (API-USER-1).
     *
     * <p>파일을 함께 받으므로 {@code multipart/form-data}만 소비한다 — 다른 Content-Type은
     * 전역 핸들러가 명세의 415로 응답한다. 입력값 위반은 400, 이메일 중복은 409다.
     */
    @PatchMapping(path = "/api/users/me/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<UserProfileResponse> updateProfile(
            @AuthenticationPrincipal UUID userId,
            @Valid @ModelAttribute UserProfileUpdateRequest request) {
        return ApiResponse.success("사용자 프로필 수정 성공",
                UserProfileResponse.from(userProfileService.updateProfile(userId, request)));
    }
}
