package com.soma.yeolo.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.soma.yeolo.global.response.ApiResponse;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 매핑되지_않은_경로는_500이_아니라_404_봉투로_응답한다() {
        NoResourceFoundException e =
                new NoResourceFoundException(HttpMethod.POST, "api/auth/unknown", null);

        ResponseEntity<ApiResponse<Void>> response = handler.handleNoResource(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().message()).isEqualTo("요청한 리소스를 찾을 수 없습니다.");
        assertThat(response.getBody().data()).isNull();
    }

    /**
     * SSE 응답은 Content-Type이 {@code text/event-stream}으로 고정돼 있어 JSON 봉투를 쓸 컨버터가
     * 없다. 이 예외들이 catch-all에 걸리면 {@code HttpMessageNotWritableException} 500이 되므로,
     * 본문을 쓰지 않는 {@code void} 핸들러로 따로 받는지 확인한다.
     */
    @Test
    void 비동기_응답_불가_예외는_본문_없는_핸들러가_받는다() throws Exception {
        Method handler = GlobalExceptionHandler.class
                .getMethod("handleAsyncRequestUnusable", Exception.class);

        assertThat(handler.getReturnType()).isEqualTo(void.class);
        assertThat(handler.getAnnotation(ExceptionHandler.class).value())
                .containsExactlyInAnyOrder(
                        AsyncRequestNotUsableException.class, AsyncRequestTimeoutException.class);

        // 예외를 넘겨도 아무것도 던지지 않고 로그만 남긴다.
        handler.invoke(new GlobalExceptionHandler(),
                new AsyncRequestNotUsableException("Response not usable after response errors."));
    }

    @Test
    void 비즈니스_예외는_ErrorCode의_상태와_메시지를_그대로_내보낸다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(ErrorCode.INVALID_REQUEST));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(400);
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_REQUEST.getMessage());
    }
}
