package com.soma.yeolo.global.exception;

import com.soma.yeolo.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 경로·쿼리 파라미터의 타입 변환 실패(예: {@code /api/places/{placeId}}에 UUID가 아닌 값).
     *
     * <p>따로 처리하지 않으면 catch-all {@link #handleUnexpected}에 걸려 500이 된다. 형식이 틀린
     * 요청은 장애가 아니라 잘못된 입력이므로 명세의 400으로 응답한다(API-PLACE-1 "잘못된 placeId" 등).
     * 메시지에 파라미터명을 담아 어느 값이 문제인지 알린다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        ErrorCode code = ErrorCode.INVALID_REQUEST;
        log.warn("요청 값의 형식이 올바르지 않다: {}={}", e.getName(), e.getValue());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getHttpStatus().value(),
                        "잘못된 %s 입니다.".formatted(e.getName())));
    }

    /**
     * 서블릿 컨테이너의 multipart 한도 초과 (API-USER-1 §"413: 프로필 이미지 용량 초과").
     *
     * <p>앱의 정책 한도({@code profile-image.max-bytes})는 서비스가 검사하지만, 컨테이너 한도를
     * 넘으면 요청 파싱 단계에서 먼저 끊겨 서비스까지 오지 않는다. 두 경로 모두 같은 413으로
     * 응답해야 클라이언트가 한 가지 처리만 하면 된다. (업로드를 받는 엔드포인트는 현재 이것뿐이다.)
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        ErrorCode code = ErrorCode.PROFILE_IMAGE_TOO_LARGE;
        log.warn("업로드 용량이 컨테이너 한도를 초과했다: {}", e.getMessage());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
    }

    /**
     * 지원하지 않는 요청 Content-Type (API-USER-1 §"415"). 처리하지 않으면 catch-all에 걸려
     * 500이 된다 — 잘못된 Content-Type은 장애가 아니라 잘못된 요청이다.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException e) {
        log.warn("지원하지 않는 Content-Type: {}", e.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        "지원하지 않는 형식입니다."));
    }

    /**
     * 매핑되지 않은 경로 요청. 핸들러가 없으면 DispatcherServlet이 정적 리소스 핸들러로 넘기고,
     * 거기서도 못 찾으면 이 예외가 나온다. catch-all {@link #handleUnexpected}에 걸리면 500 +
     * ERROR 로그가 되므로, 명세 봉투를 씌운 404로 따로 처리한다(오탈자·미구현 API 호출은 장애가 아니다).
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        ErrorCode code = ErrorCode.RESOURCE_NOT_FOUND;
        log.warn("매핑되지 않은 요청: {} /{}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
    }

    /**
     * 비동기(SSE) 응답을 더 이상 쓸 수 없는 상태에서 나오는 예외들.
     *
     * <p>클라이언트가 먼저 끊거나 스트림이 타임아웃되면 컨테이너가 서블릿 에러 디스패치를 태우고,
     * 그 예외가 catch-all {@link #handleUnexpected}에 걸린다. 하지만 응답 Content-Type은 이미
     * {@code text/event-stream}으로 고정돼 있어 JSON 봉투를 쓸 컨버터가 없다 —
     * {@code No converter for [ApiResponse] with preset Content-Type 'text/event-stream'}으로
     * 500이 찍히는 경로다. 애초에 보낼 곳이 없는 응답이므로 본문을 쓰지 않고 로그만 남긴다
     * ({@code void} 반환이면 컨버터를 타지 않는다).
     */
    @ExceptionHandler({AsyncRequestNotUsableException.class, AsyncRequestTimeoutException.class})
    public void handleAsyncRequestUnusable(Exception e) {
        log.warn("비동기 응답을 종료할 수 없다(클라이언트 이탈 또는 타임아웃): {}", e.toString());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        ErrorCode code = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(code.getHttpStatus())
                .body(ApiResponse.error(code.getHttpStatus().value(), code.getMessage()));
    }
}
