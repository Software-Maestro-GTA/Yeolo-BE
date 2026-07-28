package com.soma.yeolo.auth.controller;

import com.soma.yeolo.auth.dto.GoogleLoginRequest;
import com.soma.yeolo.auth.dto.GoogleLoginResponse;
import com.soma.yeolo.auth.dto.LogoutRequest;
import com.soma.yeolo.auth.service.AuthService;
import com.soma.yeolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** Google OAuth 로그인 (API-FB-1). */
    @PostMapping("/google")
    public ApiResponse<GoogleLoginResponse> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleLoginResponse response = authService.loginWithGoogle(request);
        return ApiResponse.success("로그인 성공", response);
    }

    /** 로그아웃 (API-FB-11). Access Token으로 식별된 사용자의 Refresh Token을 무효화한다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UUID userId,
                                    @RequestBody(required = false) LogoutRequest request) {
        authService.logout(userId);
        return ApiResponse.success("로그아웃 성공", null);
    }
}
