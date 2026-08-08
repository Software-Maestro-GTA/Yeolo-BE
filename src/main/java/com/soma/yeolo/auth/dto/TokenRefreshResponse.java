package com.soma.yeolo.auth.dto;

/**
 * 토큰 재발급 응답 (API-AUTH-3). Access/Refresh를 함께 재발급한다.
 */
public record TokenRefreshResponse(String accessToken, String refreshToken) {
}
