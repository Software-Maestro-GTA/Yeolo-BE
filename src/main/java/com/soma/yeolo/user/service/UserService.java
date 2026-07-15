package com.soma.yeolo.user.service;

import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * OAuth 로그인 시 사용자 upsert.
     * (provider, providerUserId) 기준으로 기존 사용자면 프로필/로그인 시각을 갱신하고,
     * 없으면 신규 생성한다.
     */
    @Transactional
    public User upsertOnOAuthLogin(OAuthUserInfo info) {
        return userRepository.findByProviderAndProviderUserId(info.provider(), info.providerUserId())
                .map(user -> {
                    user.updateOnLogin(info.email(), info.displayName(), info.profileImageUrl());
                    return user;
                })
                .orElseGet(() -> userRepository.save(User.createOAuthUser(
                        info.provider(), info.providerUserId(), info.email(),
                        info.displayName(), info.profileImageUrl())));
    }
}
