package com.soma.yeolo.preference.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.preference.dto.UserPreferenceRequest;
import com.soma.yeolo.preference.service.UserPreferenceService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 MBTI 등록/수정 API (API-PREF-1).
 */
@RestController
@RequiredArgsConstructor
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    /**
     * MBTI 등록/수정 (API-PREF-1). 명세상 {@code data}는 항상 {@code null}이다.
     * 누락·잘못된 값은 전역 핸들러가 명세 문구 그대로 400으로 응답한다.
     */
    @PatchMapping("/api/users/me/preferences")
    public ApiResponse<Void> updatePreferences(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UserPreferenceRequest request) {
        userPreferenceService.updateMbti(userId, request);
        return ApiResponse.success("사용자 MBTI 수정 성공", null);
    }
}
