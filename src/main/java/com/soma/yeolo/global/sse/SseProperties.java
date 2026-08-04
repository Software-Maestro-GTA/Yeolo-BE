package com.soma.yeolo.global.sse;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSE 스트림 수명 설정.
 *
 * <p>SSE 파이프라인은 구조적으로 길다 — 전처리는 외부 지오코딩 API 정책(≤1req/s)에 묶여 항목당
 * 최소 간격을 두고 직렬 반복하고, AI 분석은 실측 80초대다. 여기에 동시 요청이 겹치면 공유
 * rate limiter 대기까지 더해진다. 그러므로 <b>오래 걸리는 것은 정상이며 타임아웃으로 끊을 사유가
 * 아니다.</b> 이 값은 "정상적으로 긴 요청"을 자르지 않을 만큼 넉넉하되, 응답이 영영 오지 않는
 * 스트림이 워커·커넥션을 무한히 점유하지 않게 막는 백스톱이다.
 *
 * <p>타임아웃 체인은 안쪽이 항상 더 짧아야 한다:
 * {@code AI read timeout < SSE stream timeout}. 그래야 AI가 멈췄을 때 스트림이 살아 있는 동안
 * 명세의 {@code error} 이벤트를 내려보낼 수 있다. 순서가 뒤집히면 사용자는 아무 이벤트도 못 받고
 * 연결만 끊긴다.
 *
 * @param streamTimeoutMs SSE 스트림 상한(ms). 초과 시 {@code onTimeout}으로 정상 종료한다.
 */
@ConfigurationProperties(prefix = "sse")
public record SseProperties(
        long streamTimeoutMs
) {
}
