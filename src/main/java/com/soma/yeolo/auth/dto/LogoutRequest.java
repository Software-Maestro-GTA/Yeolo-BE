package com.soma.yeolo.auth.dto;

/**
 * 로그아웃 요청 (API-AUTH-4). {@code refreshToken}은 선택값(optional)이며,
 * 세션 종료는 Access Token으로 식별된 사용자의 Refresh Token을 무효화하는 방식으로 처리한다.
 */
public record LogoutRequest(String refreshToken) {
}
