package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private OAuthUserInfo info(String email, String displayName) {
        return new OAuthUserInfo(Provider.GOOGLE, "google-sub-123", email, displayName, "http://img");
    }

    @Test
    void 신규_사용자는_생성하여_저장한다() {
        when(userRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.upsertOnOAuthLogin(info("new@gmail.com", "신규유저"));

        assertThat(result.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(result.getProviderUserId()).isEqualTo("google-sub-123");
        assertThat(result.getEmail()).isEqualTo("new@gmail.com");
        assertThat(result.getDisplayName()).isEqualTo("신규유저");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void 기존_사용자는_프로필을_갱신하고_저장하지_않는다() {
        User existing = User.createOAuthUser(Provider.GOOGLE, "google-sub-123",
                "old@gmail.com", "옛이름", "http://old");
        when(userRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(existing));

        User result = userService.upsertOnOAuthLogin(info("new@gmail.com", "새이름"));

        assertThat(result).isSameAs(existing);
        assertThat(result.getEmail()).isEqualTo("new@gmail.com");
        assertThat(result.getDisplayName()).isEqualTo("새이름");
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * 사용자가 직접 고친 프로필(API-USER-1)은 재로그인이 되돌리지 않는다. 되돌리면 사용자에게는
     * "프로필 수정이 저장되지 않는" 것으로 보인다 — 실제로 겪은 회귀다.
     */
    @Test
    void 사용자가_고친_프로필은_재로그인_upsert가_덮어쓰지_않는다() {
        User existing = User.createOAuthUser(Provider.GOOGLE, "google-sub-123",
                "old@gmail.com", "옛이름", "http://old");
        existing.updateProfile("mine@gmail.com", "내가고친이름", "http://my-img");
        when(userRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(existing));

        User result = userService.upsertOnOAuthLogin(info("old@gmail.com", "옛이름"));

        assertThat(result.getEmail()).isEqualTo("mine@gmail.com");
        assertThat(result.getDisplayName()).isEqualTo("내가고친이름");
        assertThat(result.getProfileImageUrl()).isEqualTo("http://my-img");
        verify(userRepository, never()).save(any(User.class));
    }
}
