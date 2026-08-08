package com.soma.yeolo.user.service.port;

import java.util.UUID;

/**
 * 회원탈퇴 시 사용자의 취향 프로필을 파기하기 위한 출력 포트 (API-USER-2).
 * user 계층이 tasteprofile 구현에 직접 의존하지 않도록 인터페이스를 user 쪽에 두고,
 * tasteprofile 계층의 어댑터가 이를 구현한다. ({@link RefreshTokenRevoker}와 같은 방식,
 * docs/architecture.md §1-2)
 *
 * <p>취향 프로필은 이름·이메일 같은 식별정보는 아니지만 사진 EXIF의 위치·시간에서 파생된
 * <b>개인의 이동 이력</b>이다. 탈퇴 시 계정 식별정보만 지우고 이걸 남기면 파기가 반쪽이 된다.
 */
public interface TasteProfileEraser {

    /** 사용자의 취향 프로필을 모두 삭제한다. 없으면 무시(멱등). */
    void eraseAll(UUID userId);
}
