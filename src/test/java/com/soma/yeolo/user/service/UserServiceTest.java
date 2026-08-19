package com.soma.yeolo.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.domain.UserStatus;
import com.soma.yeolo.user.entity.User;
import com.soma.yeolo.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private OAuthUserInfo info(String email, String displayName) {
        return new OAuthUserInfo(Provider.GOOGLE, "google-sub-123", email, displayName, "http://img");
    }

    /**
     * 탈퇴 표시만 남고 <b>OAuth 식별자·식별정보는 그대로인</b> 사용자. 탈퇴 최초 구현(#42)이 남긴
     * 실제 데이터 모양이다 — 식별자 치환과 개인정보 파기는 그 다음 개정(#44)에서 추가됐다.
     */
    private User legacyWithdrawnUser(UUID id) {
        User user = User.createOAuthUser(Provider.GOOGLE, "google-sub-123",
                "old@gmail.com", "옛이름", "http://old");
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "status", UserStatus.DELETED);
        ReflectionTestUtils.setField(user, "deletedAt", Instant.now());
        return user;
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
        existing.updateProfile("내가고친이름", "http://my-img");
        when(userRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(existing));

        User result = userService.upsertOnOAuthLogin(info("old@gmail.com", "옛이름"));

        assertThat(result.getDisplayName()).isEqualTo("내가고친이름");
        assertThat(result.getProfileImageUrl()).isEqualTo("http://my-img");
        verify(userRepository, never()).save(any(User.class));
    }

    /**
     * 탈퇴 계정을 되살리면 로그인만 되고 아무것도 못 하는 상태가 된다 — 인증 필터가 탈퇴자를
     * 차단하므로 이후 모든 API가 401이다. 코스 목록은 비어 보이는데 로그인 응답의
     * {@code recentCourseId}만 옛 코스를 가리키는 신고가 여기서 나왔다.
     */
    @Test
    void 식별자가_남은_탈퇴_계정으로_재로그인하면_되살리지_않고_새_사용자를_만든다() {
        User withdrawn = legacyWithdrawnUser(UUID.randomUUID());
        when(userRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(withdrawn));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.upsertOnOAuthLogin(info("new@gmail.com", "재가입유저"));

        assertThat(result).isNotSameAs(withdrawn);
        assertThat(result.getProviderUserId()).isEqualTo("google-sub-123");
        assertThat(result.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(result.getDeletedAt()).isNull();
        assertThat(result.getEmail()).isEqualTo("new@gmail.com");
    }

    /** 되살아난 계정에는 {@code updateOnLogin}이 실행돼, 탈퇴 때 파기한 개인정보가 복원된다. */
    @Test
    void 탈퇴_계정의_개인정보는_재로그인으로_복원되지_않고_식별자도_회수된다() {
        UUID withdrawnId = UUID.randomUUID();
        User withdrawn = legacyWithdrawnUser(withdrawnId);
        when(userRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(withdrawn));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.upsertOnOAuthLogin(info("new@gmail.com", "재가입유저"));

        assertThat(withdrawn.getProviderUserId()).isEqualTo("deleted:" + withdrawnId);
        assertThat(withdrawn.getEmail()).isNull();
        assertThat(withdrawn.getDisplayName()).isNull();
        assertThat(withdrawn.getProfileImageUrl()).isNull();
        assertThat(withdrawn.getDeletedAt()).isNotNull();
    }

    /**
     * 식별자 회수를 먼저 flush하지 않으면 새 행 INSERT가 옛 행 UPDATE보다 앞서 나가
     * {@code (provider, provider_user_id)} 유니크 제약을 위반한다(로그인이 500).
     */
    @Test
    void 식별자_회수를_새_사용자_저장보다_먼저_반영한다() {
        User withdrawn = legacyWithdrawnUser(UUID.randomUUID());
        when(userRepository.findByProviderAndProviderUserId(Provider.GOOGLE, "google-sub-123"))
                .thenReturn(Optional.of(withdrawn));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        userService.upsertOnOAuthLogin(info("new@gmail.com", "재가입유저"));

        InOrder order = inOrder(userRepository);
        order.verify(userRepository).saveAndFlush(withdrawn);
        order.verify(userRepository).save(any(User.class));
    }
}
