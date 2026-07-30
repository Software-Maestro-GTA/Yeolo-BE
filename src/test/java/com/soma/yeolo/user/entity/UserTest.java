package com.soma.yeolo.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.user.domain.Provider;
import com.soma.yeolo.user.domain.UserStatus;
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
    void 재로그인_시_프로필을_최신화하고_식별정보는_유지한다() {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "old@gmail.com", "옛이름", "http://old");

        user.updateOnLogin("new@gmail.com", "새이름", "http://new");

        // 프로필은 갱신
        assertThat(user.getEmail()).isEqualTo("new@gmail.com");
        assertThat(user.getDisplayName()).isEqualTo("새이름");
        assertThat(user.getProfileImageUrl()).isEqualTo("http://new");
        // 제공자/식별자는 불변
        assertThat(user.getProvider()).isEqualTo(Provider.GOOGLE);
        assertThat(user.getProviderUserId()).isEqualTo("sub-1");
    }

    @Test
    void 탈퇴하면_deleted_상태로_전환하고_탈퇴시각을_기록하며_개인정보를_파기한다() {
        User user = User.createOAuthUser(Provider.GOOGLE, "sub-1",
                "u@gmail.com", "홍길동", "http://img");

        user.withdraw();

        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
        // 개인정보 영구 파기 (API-FB-12)
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
