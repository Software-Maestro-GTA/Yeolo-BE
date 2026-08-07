package com.soma.yeolo.preference.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 사용자 MBTI 등록/수정 요청 (API-PREF-1).
 *
 * <p>16유형 여부는 {@link com.soma.yeolo.preference.domain.Mbti}가 판정하므로 여기서는 누락만
 * 걸러낸다. 위반 메시지는 명세의 실패 응답 문구를 그대로 쓴다.
 */
public record UserPreferenceRequest(

        @NotBlank(message = "MBTI 입력값을 확인해주세요.")
        String mbti
) {
}
