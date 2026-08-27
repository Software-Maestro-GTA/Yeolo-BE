package com.soma.yeolo.user.dto;

import com.soma.yeolo.user.entity.User;

/**
 * 사용자 프로필 수정 응답의 {@code data} 페이로드 (API-USER-1).
 *
 * <p>필드명·값은 명세를 그대로 따른다. provider/status는 도메인 Enum의 소문자 값,
 * lastLoginAt은 ISO-8601 문자열이다. DOM-1대로 email·displayName·profileImageUrl은 null일 수 있다.
 */
public record UserProfileResponse(UserSummary user) {

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

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(new UserSummary(
                user.getId().toString(),
                user.getProvider().getValue(),
                user.getEmail(),
                user.getDisplayName(),
                user.getProfileImageUrl(),
                user.getStatus().getValue(),
                user.getLastLoginAt() == null ? null : user.getLastLoginAt().toString()
        ));
    }
}
