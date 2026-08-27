package com.soma.yeolo.auth.controller;

import com.soma.yeolo.auth.dto.AppleLoginRequest;
import com.soma.yeolo.auth.dto.AppleLoginResponse;
import com.soma.yeolo.auth.dto.GoogleLoginRequest;
import com.soma.yeolo.auth.dto.GoogleLoginResponse;
import com.soma.yeolo.auth.dto.LogoutRequest;
import com.soma.yeolo.auth.dto.TokenRefreshRequest;
import com.soma.yeolo.auth.dto.TokenRefreshResponse;
import com.soma.yeolo.auth.service.AuthService;
import com.soma.yeolo.global.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";

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

    /**
     * 토큰 재발급 (API-AUTH-3). Refresh Token을 검증하고 Access/Refresh를 함께 재발급한다.
     *
     * <p>명세가 Refresh Token을 {@code Authorization: Bearer} 헤더와 요청 본문 양쪽에 적어 둬서
     * 둘 다 받는다 — 본문을 우선하고, 없으면 헤더의 Bearer 값을 쓴다. 어느 쪽도 없으면 서비스가 401.
     * 이 엔드포인트는 만료된 Access Token으로 호출되는 자리이므로 인증 없이 열려 있다.
     */
    @PostMapping("/refresh")
    public ApiResponse<TokenRefreshResponse> refresh(
            @RequestBody(required = false) TokenRefreshRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        TokenRefreshResponse response = authService.refresh(resolveRefreshToken(request, authorization));
        return ApiResponse.success("토큰 재발급 성공", response);
    }

    private static String resolveRefreshToken(TokenRefreshRequest request, String authorization) {
        if (request != null && StringUtils.hasText(request.refreshToken())) {
            return request.refreshToken();
        }
        if (StringUtils.hasText(authorization) && authorization.startsWith(BEARER_PREFIX)) {
            return authorization.substring(BEARER_PREFIX.length()).trim();
        }
        return null;
    }

    /** 로그아웃 (API-AUTH-4). Access Token으로 식별된 사용자의 Refresh Token을 무효화한다. */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UUID userId,
                                    @RequestBody(required = false) LogoutRequest request) {
        authService.logout(userId);
        return ApiResponse.success("로그아웃 성공", null);
    }
}
