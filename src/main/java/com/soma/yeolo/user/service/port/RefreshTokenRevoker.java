package com.soma.yeolo.user.service.port;

import java.util.UUID;

/**
 * 회원탈퇴 시 사용자의 세션(Refresh Token)을 무효화하기 위한 출력 포트.
 * user 계층이 auth 구현에 직접 의존하지 않도록 인터페이스를 user 쪽에 두고,
 * auth 계층의 어댑터가 이를 구현한다. (docs/architecture.md §1-2)
 */
public interface RefreshTokenRevoker {

    /** 사용자의 Refresh Token을 무효화한다. 없으면 무시(멱등). */
    void revoke(UUID userId);
}
