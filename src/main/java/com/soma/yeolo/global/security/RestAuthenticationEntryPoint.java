package com.soma.yeolo.global.security;

import com.soma.yeolo.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증되지 않은 보호 리소스 접근 시 명세의 {@code {status, message, data:null}} 봉투로 401을 응답한다.
 * (예: API-FB-2 401 — 인증 필요/토큰 만료)
 *
 * <p>고정된 에러 봉투만 직렬화하므로 {@code ObjectMapper} 빈에 의존하지 않고 직접 기록한다.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ErrorCode code = ErrorCode.UNAUTHORIZED;
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String safeMessage = code.getMessage()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        String body = "{\"status\":%d,\"message\":\"%s\",\"data\":null}".formatted(
                code.getHttpStatus().value(), safeMessage);
        response.getWriter().write(body);
    }
}
