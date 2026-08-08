package com.soma.yeolo.global.security;

import java.util.UUID;

/**
 * 탈퇴한 사용자인지 판정하는 인증 보조 포트 (API-USER-2).
 *
 * <p>JWT는 자체 검증만으로는 발급 이후 사정을 알 수 없다. 탈퇴해도 이미 발급된 Access Token은
 * 서명·만료가 그대로 유효해서, 토큰 수명(기본 1시간)이 다할 때까지 탈퇴자가 보호 API를 계속
 * 호출할 수 있다. Refresh Token은 탈퇴 시 지워지므로 갱신은 막히지만 그 창은 남는다.
 * 인증 시점에 계정 상태를 한 번 확인해 그 창을 닫는다.
 *
 * <p>인터페이스를 {@code global.security}에 두고 {@code user} 계층이 구현한다 — 보안 필터가
 * 사용자 도메인의 리포지토리에 직접 의존하지 않게 하기 위함이다. (docs/architecture.md §1-2)
 */
public interface WithdrawnUserChecker {

    /**
     * 탈퇴 처리된 사용자면 {@code true}. 존재하지 않는 사용자는 {@code false}를 돌려주고
     * (인증은 통과) 실제 처리 단계에서 404로 걸러지게 둔다 — 인증 필터가 사용자 존재 여부까지
     * 책임지면 요청마다 판정이 중복된다.
     */
    boolean isWithdrawn(UUID userId);
}
