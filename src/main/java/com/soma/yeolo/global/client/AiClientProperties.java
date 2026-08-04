package com.soma.yeolo.global.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 내부 API 연동 설정. 성향 분석·코스 생성 등 여러 도메인 클라이언트가 공유하는 내부 게이트웨이
 * 접속 정보이므로 {@code global}에 둔다. 내부 인증 키는 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 *
 * <p>LLM 분석은 실측 80초대이므로 응답 타임아웃은 넉넉해야 한다. 이 값은 소켓 read 타임아웃
 * (연속 무응답 허용 시간)이며, {@code sse.stream-timeout-ms}보다 반드시 짧게 유지한다 —
 * 그래야 AI가 멈췄을 때 스트림이 살아 있는 동안 {@code error} 이벤트를 내려보낼 수 있다.
 * ({@link com.soma.yeolo.global.sse.SseProperties} 참고)
 *
 * @param baseUrl          AI 서버 베이스 URL (예: http://localhost:8000)
 * @param apiKey           내부 인증 키 (X-Internal-Api-Key)
 * @param connectTimeoutMs 연결 타임아웃(ms). 연결 자체는 즉시 되거나 안 되거나이므로 짧게 둔다.
 * @param readTimeoutMs    응답 타임아웃(ms). 연속 무응답 허용 시간.
 */
@ConfigurationProperties(prefix = "ai.internal")
public record AiClientProperties(
        String baseUrl,
        String apiKey,
        long connectTimeoutMs,
        long readTimeoutMs
) {
}
