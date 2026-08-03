package com.soma.yeolo.auth.dto;

import com.soma.yeolo.user.entity.User;

/**
 * Google OAuth 로그인 응답 (API-AUTH-1). 필드명·값은 명세를 그대로 따른다.
 * provider/status는 도메인 Enum의 소문자 값("google"/"active"), lastLoginAt은 ISO-8601.
 * doOnboarding은 온보딩(Intro) 유도 여부로, true면 앱은 최초 진입 시 온보딩 화면으로 분기한다.
 */
public record GoogleLoginResponse(
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

    public static GoogleLoginResponse from(User user, boolean doOnboarding,
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
        return new GoogleLoginResponse(summary, doOnboarding, accessToken, refreshToken);
    }
}
