package com.soma.yeolo.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 HTTP 요청의 진입과 처리 결과를 한 줄씩 남기는 기본 접근 로그 필터.
 * "요청이 들어왔다 / 이렇게 처리됐다(상태코드·소요시간)"를 확인할 수 있게 한다.
 * 요청마다 짧은 요청 ID를 발급해 진입/완료 두 줄을 짝지을 수 있게 한다.
 * 상태코드에 따라 로그 레벨을 달리한다: 5xx=error, 4xx=warn, 그 외=info.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        MDC.put(MDC_REQUEST_ID, newRequestId());
        String method = request.getMethod();
        String path = requestPath(request);
        long start = System.nanoTime();

        log.info("--> {} {}", method, path);
        try {
            filterChain.doFilter(request, response);
        } finally {
            long tookMs = (System.nanoTime() - start) / 1_000_000;
            logCompletion(method, path, response.getStatus(), tookMs);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private void logCompletion(String method, String path, int status, long tookMs) {
        if (status >= 500) {
            log.error("<-- {} {} {} ({}ms)", method, path, status, tookMs);
        } else if (status >= 400) {
            log.warn("<-- {} {} {} ({}ms)", method, path, status, tookMs);
        } else {
            log.info("<-- {} {} {} ({}ms)", method, path, status, tookMs);
        }
    }

    /** 헬스 프로브(k8s readiness/liveness)는 초당 다수 호출되어 로그를 오염시키므로 제외한다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/health");
    }

    private String requestPath(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private String newRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
