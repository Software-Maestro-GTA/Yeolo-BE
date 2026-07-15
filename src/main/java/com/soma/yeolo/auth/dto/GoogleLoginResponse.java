package com.soma.yeolo.auth.dto;

import com.soma.yeolo.user.entity.User;

/**
 * Google OAuth 로그인 응답 (API-FB-1). 필드명은 명세를 그대로 따른다
 * (엔티티의 displayName/profileImageUrl → 응답의 nickname/profileImage).
 */
public record GoogleLoginResponse(
        UserSummary user,
        String accessToken,
        String refreshToken
) {

    public record UserSummary(
            String userId,
            String email,
            String nickname,
            String profileImage,
            boolean hasTasteProfile
    ) {
    }

    public static GoogleLoginResponse from(User user, boolean hasTasteProfile,
                                           String accessToken, String refreshToken) {
        UserSummary summary = new UserSummary(
                user.getId().toString(),
                user.getEmail(),
                user.getDisplayName(),
                user.getProfileImageUrl(),
                hasTasteProfile
        );
        return new GoogleLoginResponse(summary, accessToken, refreshToken);
    }
}
