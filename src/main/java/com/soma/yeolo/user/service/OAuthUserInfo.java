package com.soma.yeolo.user.service;

import com.soma.yeolo.user.domain.Provider;

/**
 * OAuth 제공자로부터 확인된 사용자 식별/프로필 정보. 특정 제공자에 종속되지 않는 형태로 전달한다.
 */
public record OAuthUserInfo(
        Provider provider,
        String providerUserId,
        String email,
        String displayName,
        String profileImageUrl
) {
}
