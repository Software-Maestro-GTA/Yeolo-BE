package com.soma.yeolo.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 명세(API-FB-1 등)의 공통 응답 봉투. {@code {status, message, data}} 형식을 그대로 따른다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResponse<T>(int status, String message, T data) {

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static ApiResponse<Void> error(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}
