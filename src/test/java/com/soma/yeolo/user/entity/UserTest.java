package com.soma.yeolo.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.domain.UserStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * 순수 도메인 테스트 — 프레임워크/외부 의존성 없이 엔티티 행위만 검증한다(테스트 더블 미사용).
 */
class UserTest {

    @Test
    void 신규_OAuth_사용자는_active_상태로_로그인_시각을_기록하며_생성된다() {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "u@gmail.com", "홍길동", "http://img");

        assertThat(user.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(user.getProviderUserId()).isEqualTo("sub-1");
        assertThat(user.getEmail()).isEqualTo("u@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("홍길동");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://img");
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(user.getDeletedAt()).isNull();
    }

    @Test
    void 재로그인_시_고치지_않은_프로필은_제공자_값을_따라가고_식별정보는_유지한다() throws InterruptedException {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "old@gmail.com", "옛이름", "http://old");
        Instant firstLoginAt = user.getLastLoginAt();
        Thread.sleep(1);  // Instant.now() 해상도상 같은 값이 나오지 않도록

        user.updateOnLogin("new@gmail.com", "새이름", "http://new");

        // 손대지 않은 항목은 제공자를 따라간다
        assertThat(user.getEmail()).isEqualTo("new@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("새이름");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://new");
        assertThat(user.getLastLoginAt()).isAfter(firstLoginAt);
        // 제공자/식별자는 불변
        assertThat(user.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(user.getProviderUserId()).isEqualTo("sub-1");
    }

    @Test
    void 사용자가_수정한_프로필은_재로그인해도_유지된다() {
        // 제공자 프로필로 가입한 뒤 사용자가 직접 고친 상황 (API-USER-1).
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "oauth@gmail.com", "구글이름", "http://oauth-img");
        user.updateProfile("mine@gmail.com", "내가고친이름", "http://my-img");

        user.updateOnLogin("oauth@gmail.com", "구글이름", "http://oauth-img");

        // 로그인이 제공자 값으로 되돌리면 안 된다 — 수정이 저장되지 않는 것처럼 보인다.
        assertThat(user.getEmail()).isEqualTo("mine@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("내가고친이름");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://my-img");
    }

    @Test
    void 고친_항목만_굳고_나머지는_계속_제공자를_따라간다() {
        // 이름만 고친 사용자. 부분 수정이므로 이메일·이미지는 여전히 제공자 소관이다.
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "old@gmail.com", "구글이름", "http://old");
        user.updateProfile(null, "내가고친이름", null);

        user.updateOnLogin("new@gmail.com", "바뀐구글이름", "http://new");

        assertThat(user.getDisplayName()).isEqualTo("내가고친이름");
        assertThat(user.getEmail()).isEqualTo("new@gmail.com");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://new");
    }

    @Test
    void 제공자가_주지_않은_항목은_로그인이_지우지_않는다() {
        // Apple은 최초 동의 이후 이름·사진을 주지 않고 이메일도 생략될 수 있다(DOM-1).
        // 미제공(null)은 "지워 달라"가 아니라 "모른다"이므로 기존 값을 그대로 둔다.
        User user = User.createOAuthUser(Provider.APPLE, "apple-sub",
                "user@privaterelay.appleid.com", "이름", "http://img");

        user.updateOnLogin(null, null, null);

        assertThat(user.getEmail()).isEqualTo("user@privaterelay.appleid.com");
        assertThat(user.getDisplayName()).isEqualTo("이름");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://img");
    }

    @Test
    void 프로필_수정은_전달된_항목만_반영한다() {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "old@gmail.com", "옛이름", "http://old");

        user.updateProfile(null, "새이름", null);

        // 이름만 바꿨는데 이메일·이미지가 지워지면 안 된다 (API-USER-1 nullable 정책).
        assertThat(user.getDisplayName()).isEqualTo("새이름");
        assertThat(user.getEmail()).isEqualTo("old@gmail.com");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://old");
    }

    @Test
    void 프로필_수정으로_세_항목을_한번에_갱신할_수_있다() {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1", null, null, null);

        user.updateProfile("new@gmail.com", "새이름", "http://new");

        assertThat(user.getEmail()).isEqualTo("new@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("새이름");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://new");
    }

    @Test
    void 탈퇴하면_deleted_상태로_전환하고_탈퇴시각을_기록하며_개인정보를_파기한다() {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "u@gmail.com", "홍길동", "http://img");

        user.withdraw();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
        // 개인정보 영구 파기 (API-USER-2)
        assertThat(user.getEmail()).isNull();
        assertThat(user.getDisplayName()).isNull();
        assertThat(user.getProfileImageUrl()).isNull();
        // OAuth 식별자는 익명 토큰으로 치환 — 원본 sub 제거 + 재가입 시 새 사용자로 인식
        assertThat(user.getProviderUserId()).isNotEqualTo("sub-1");
        assertThat(user.getProviderUserId()).startsWith("deleted:");
    }

    @Test
    void 이미_탈퇴한_계정을_다시_탈퇴해도_상태와_탈퇴시각은_유지된다() {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "u@gmail.com", "홍길동", "http://img");
        user.withdraw();
        var firstDeletedAt = user.getDeletedAt();

        user.withdraw();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isEqualTo(firstDeletedAt);
    }
}
