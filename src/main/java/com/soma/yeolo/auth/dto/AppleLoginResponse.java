package com.soma.yeolo.auth.dto;

import com.soma.yeolo.user.entity.User;

/**
 * Apple OAuth 로그인 응답 (API-AUTH-2). 필드명·값은 명세를 그대로 따른다.
 * provider/status는 도메인 Enum의 소문자 값("apple"/"active"), lastLoginAt은 ISO-8601.
 * Apple은 id_token에 이름/프로필 이미지를 제공하지 않으므로 displayName/profileImageUrl은
 * 최초 인증 시 클라이언트가 전달하지 않는 한 null이다. doOnboarding은 온보딩(Intro) 유도 여부다.
 */
public record AppleLoginResponse(
        UserSummary user,
        boolean doOnboarding,
        String accessToken,
        String refreshToken
) {

    public record UserSummary(
            String userId,
            String provider,
            String email,
            String displayName,
            String profileImageUrl,
            String status,
            String lastLoginAt
    ) {
    }

    public static AppleLoginResponse from(User user, boolean doOnboarding,
                                          String accessToken, String refreshToken) {
        UserSummary summary = new UserSummary(
                user.getId().toString(),
                user.getProvider().getValue(),
                user.getEmail(),
                user.getDisplayName(),
                user.getProfileImageUrl(),
                user.getStatus().getValue(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString()
        );
        return new AppleLoginResponse(summary, doOnboarding, accessToken, refreshToken);
    }
}
