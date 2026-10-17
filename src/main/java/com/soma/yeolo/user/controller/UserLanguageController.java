package com.soma.yeolo.user.controller;

import com.soma.yeolo.global.response.ApiResponse;
import com.soma.yeolo.user.dto.UserLanguageRequest;
import com.soma.yeolo.user.dto.UserLanguageResponse;
import com.soma.yeolo.user.service.UserLanguageService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 내 언어 설정 (API-USER-3 조회 / API-USER-4 변경). 인증 필요. */
@RestController
@RequiredArgsConstructor
public class UserLanguageController {

    private final UserLanguageService userLanguageService;

    @GetMapping("/api/users/me/language")
    public ApiResponse<UserLanguageResponse> getLanguage(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.success("언어 설정 조회 성공",
                new UserLanguageResponse(userLanguageService.findLanguage(userId)));
    }

    @PostMapping("/api/users/me/language")
    public ApiResponse<UserLanguageResponse> updateLanguage(@AuthenticationPrincipal UUID userId,
                                                            @Valid @RequestBody UserLanguageRequest request) {
        return ApiResponse.success("언어 설정 변경 성공",
                new UserLanguageResponse(userLanguageService.updateLanguage(userId, request.languageText())));
    }
}
