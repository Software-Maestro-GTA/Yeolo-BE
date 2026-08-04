package com.soma.yeolo.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Apple OAuth 로그인 요청 (API-AUTH-2). {@code code}, {@code redirectUri}는 필수,
 * {@code idToken}은 선택. 클라이언트가 Apple에서 받은 {@code idToken}을 함께 넘기면
 * 코드 교환 없이 해당 토큰을 바로 검증한다(없으면 {@code code}를 교환해 id_token을 얻는다).
 */
public record AppleLoginRequest(
        @NotBlank(message = "인가 코드가 유효하지 않습니다.") String code,
        @NotBlank(message = "리다이렉트 URI가 유효하지 않습니다.") String redirectUri,
        String idToken
) {
}
