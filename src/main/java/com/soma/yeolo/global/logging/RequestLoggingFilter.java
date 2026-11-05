package com.soma.yeolo.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
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
 *
 * <p>앱이 보내는 {@code X-App-Version}(2026-11 명세 개정으로 FE→BE 전 API 의 공통 헤더)은 진입 로그에 함께
 * 남긴다 — 장애 재현 시 "어느 앱 버전이 보낸 요청인가"를 가르기 위해서다. 명세는 헤더의 존재만 정하고 서버의
 * 용도는 정하지 않았으므로 없어도 거절하지 않는다(강제 업데이트 판정은 별도 요구사항). 값은 로그 한 줄에
 * 들어가므로 길이를 자르고 허용 문자(문자·숫자·{@code . _ + ( ) -}·공백) 밖은 지운다 — 금지 목록(제어 문자)
 * 방식은 유니코드 줄 구분자(U+2028, NEL)를 놓쳐 가짜 로그 줄을 만들 수 있다(로그 인젝션 방지).
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String MDC_REQUEST_ID = "requestId";
    static final String APP_VERSION_HEADER = "X-App-Version";
    private static final int APP_VERSION_MAX_LENGTH = 32;
    /** 허용 문자 밖(공백 포함)은 연속 구간마다 공백 하나로 — 한 번의 치환으로 정리와 공백 축약을 같이 한다. 매번 컴파일하지 않는다. */
    private static final Pattern APP_VERSION_NOISE = Pattern.compile("[^\\p{L}\\p{N}._+()-]+");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        MDC.put(MDC_REQUEST_ID, newRequestId());
        String method = request.getMethod();
        String path = requestPath(request);
        long start = System.nanoTime();

        String appVersion = appVersion(request);
        if (appVersion == null) {
            log.info("--> {} {}", method, path);
        } else {
            log.info("--> {} {} app={}", method, path, appVersion);
        }
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

    /** 헤더가 없거나 비면 null. 로그용이므로 허용 문자 밖을 지우고 공백을 정리한 뒤 길이를 자른다. */
    static String appVersion(HttpServletRequest request) {
        String raw = request.getHeader(APP_VERSION_HEADER);
        if (raw == null) {
            return null;
        }
        String cleaned = APP_VERSION_NOISE.matcher(raw).replaceAll(" ").strip();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > APP_VERSION_MAX_LENGTH ? cleaned.substring(0, APP_VERSION_MAX_LENGTH) : cleaned;
    }

    private String newRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
