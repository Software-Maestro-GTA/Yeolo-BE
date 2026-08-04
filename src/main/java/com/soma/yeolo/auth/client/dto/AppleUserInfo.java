package com.soma.yeolo.auth.client.dto;

/**
 * Apple id_token(JWT)에서 검증·추출한 사용자 식별 정보.
 * {@code sub}가 제공자 기준 사용자 고유 식별자(DOM-3 providerUserId)다.
 * Apple은 id_token에 이름/프로필 이미지를 제공하지 않으며, {@code email}은 최초 인증 이후
 * 생략될 수 있다. {@code emailVerified}가 명시적으로 false면 인증 실패로 처리한다.
 */
public record AppleUserInfo(
        String sub,
        String email,
        Boolean emailVerified
) {
}
