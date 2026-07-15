package com.soma.yeolo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Google OAuth 로그인 요청 (API-FB-1). {@code code}는 필수, {@code redirectUri}는 선택.
 */
public record GoogleLoginRequest(
        @NotBlank(message = "인가 코드가 유효하지 않습니다.") String code,
        String redirectUri
) {
}
