package com.soma.yeolo.user.domain;

/**
 * 다른 사용자에게 보여줄 수 있는 최소 프로필 (DOM-1). 공유 링크 미리보기의 초대자 정보
 * (API-SHARE-2 §{@code inviter})처럼 "남에게 노출되는" 자리에 쓴다.
 *
 * <p>이메일·OAuth 식별자 같은 식별정보는 담지 않는다 — 미리보기는 비로그인 사용자도 볼 수 있어
 * 노출 범위가 가장 넓은 응답이다. 두 항목 모두 명세상 nullable이며, 탈퇴한 사용자는 값이 파기돼
 * 둘 다 {@code null}이 된다.
 *
 * @param displayName     표시 이름
 * @param profileImageUrl 프로필 이미지 URL
 */
public record UserDisplayProfile(
        String displayName,
        String profileImageUrl
) {
}
