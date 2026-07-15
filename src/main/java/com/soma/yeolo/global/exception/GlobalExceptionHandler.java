package com.soma.yeolo.global.exception;

import com.soma.yeolo.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 핸들러. 모든 에러 응답을 명세의 {@code {status, message, data:null}} 봉투로 통일한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        if (code.getHttpStatus().is5xxServerError()) {
            log.error("BusinessException(5xx): {}", code, e);
        } else {
            log.warn("BusinessException: {} - {}", code, e.getMessage());
        }
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : code.getMessage();
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getHttpStatus().value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
    }
}
