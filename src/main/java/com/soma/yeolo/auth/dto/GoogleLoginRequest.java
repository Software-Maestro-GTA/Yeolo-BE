package com.soma.yeolo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Google OAuth 로그인 요청 (API-FB-1). {@code code}, {@code redirectUri} 모두 필수.
 */
public record GoogleLoginRequest(
        @NotBlank(message = "인가 코드가 유효하지 않습니다.") String code,
        @NotBlank(message = "리다이렉트 URI가 유효하지 않습니다.") String redirectUri
) {
}
