package com.soma.yeolo.auth.dto;

/**
 * 토큰 재발급 요청 (API-AUTH-3).
 *
 * <p>명세는 Refresh Token을 {@code Authorization: Bearer} 헤더와 이 본문 양쪽에 적어 두었다.
 * 어느 쪽으로 보내도 동작하도록 컨트롤러가 둘 다 받는다(본문 우선).
 */
public record TokenRefreshRequest(String refreshToken) {
}
