package com.soma.yeolo.user.dto;

import com.soma.yeolo.global.exception.ErrorCode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * 언어 설정 변경 요청 (API-USER-4). 명세는 누락·null·빈 문자열·잘못된 타입·미지원 코드를 모두 400 한
 * 문구로 묶는다. 누락·null 은 여기서, 나머지는 서비스가 같은 문구({@code UNSUPPORTED_LANGUAGE})로 거절한다.
 *
 * <p>필드를 {@code Object}로 받는 이유: Jackson 은 숫자 {@code 123}을 {@code String} 필드에 "123"으로
 * 조용히 바꿔 넣는다. 전역 강제 변환 설정을 끄면 다른 요청의 숫자 문자열("3")까지 깨지므로, 이 DTO 에서만
 * 문자열이 아닌 값을 걸러낸다({@link #languageText()}).
 */
public record UserLanguageRequest(
        @NotNull(message = ErrorCode.Messages.UNSUPPORTED_LANGUAGE)
        Object language
) {

    /** 문자열로 온 값. 검증을 통과했다면 비어 있지 않은 문자열이다. */
    public String languageText() {
        return language instanceof String text ? text : null;
    }

    /** 빈 문자열과 문자열이 아닌 타입(숫자·배열·객체)을 검증 단계에서 같은 문구로 거절한다. null 은 {@code @NotNull} 몫. */
    @AssertTrue(message = ErrorCode.Messages.UNSUPPORTED_LANGUAGE)
    public boolean isLanguageText() {
        return language == null || (language instanceof String text && !text.isBlank());
    }
}
