package com.soma.yeolo.auth.controller;

import com.soma.yeolo.auth.dto.AppleLoginRequest;
import com.soma.yeolo.auth.dto.AppleLoginResponse;
import com.soma.yeolo.auth.dto.GoogleLoginRequest;
import com.soma.yeolo.auth.dto.GoogleLoginResponse;
import com.soma.yeolo.auth.service.AuthService;
import com.soma.yeolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Google OAuth 로그인 (API-AUTH-1). */
    @PostMapping("/google")
    public ApiResponse<GoogleLoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleLoginResponse response = authService.loginWithGoogle(request);
        return ApiResponse.success("로그인 성공", response);
    }

    /** Apple OAuth 로그인 (API-AUTH-2). */
    @PostMapping("/apple")
    public ApiResponse<AppleLoginResponse> appleLogin(@Valid @RequestBody AppleLoginRequest request) {
        AppleLoginResponse response = authService.loginWithApple(request);
        return ApiResponse.success("로그인 성공", response);
    }
}
