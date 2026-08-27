package com.soma.yeolo.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 사진 데이터 분석 동의 저장 요청 (API-PREF-2).
 *
 * <p>{@code agreed=false}는 철회를 뜻한다(별도 철회 API 없음). {@code Boolean} 래퍼로 받아야
 * 필드 누락과 {@code false}를 구분해 400으로 걸러낼 수 있다.
 * 위반 메시지는 명세의 실패 응답 문구를 그대로 쓴다.
 */
public record PhotoConsentRequest(

        @NotNull(message = "사진 데이터 분석 동의 입력값을 확인해주세요.")
        Boolean agreed,

        @NotBlank(message = "사진 데이터 분석 동의 입력값을 확인해주세요.")
        String consentVersion
) {
}
