package com.soma.yeolo.user.service;

import com.soma.yeolo.user.domain.UserDisplayProfile;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDisplayProfileReader {

    private final UserRepository userRepository;

    /**
     * OAuth 로그인 시 사용자 upsert.
     * (provider, providerUserId) 기준으로 기존 사용자면 프로필/로그인 시각을 갱신하고,
     * 없으면 신규 생성한다.
     *
     * <p>프로필 갱신은 사용자가 API-USER-1로 직접 고치지 않은 항목에만 적용된다 — 판단은
     * 엔티티가 한다({@link User#updateOnLogin}).
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

    /**
     * 남에게 보여줄 사용자 표시 프로필 (API-SHARE-2 §{@code inviter}).
     *
     * <p>탈퇴 여부로 거르지 않는다 — 탈퇴 시 이름·이미지가 이미 파기돼 두 값 모두 {@code null}로
     * 나오므로, 명세가 허용하는 {@code null} 응답과 결과가 같다.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<UserDisplayProfile> findDisplayProfile(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> new UserDisplayProfile(user.getDisplayName(), user.getProfileImageUrl()));
    }
}
