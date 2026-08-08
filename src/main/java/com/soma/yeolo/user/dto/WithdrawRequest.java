package com.soma.yeolo.user.dto;

/**
 * 회원탈퇴 요청 (API-USER-2). {@code reason}은 선택값(optional)으로 탈퇴 사유이며,
 * 없어도 탈퇴 처리에 영향을 주지 않는다.
 */
public record WithdrawRequest(String reason) {
}
