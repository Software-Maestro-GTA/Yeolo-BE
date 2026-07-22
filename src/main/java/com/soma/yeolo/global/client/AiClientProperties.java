package com.soma.yeolo.global.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 내부 API 연동 설정. 성향 분석·코스 생성 등 여러 도메인 클라이언트가 공유하는 내부 게이트웨이
 * 접속 정보이므로 {@code global}에 둔다. 내부 인증 키는 커밋 금지 — 환경변수/로컬 설정으로 주입한다.
 *
 * @param baseUrl AI 서버 베이스 URL (예: http://localhost:8000)
 * @param apiKey  내부 인증 키 (X-Internal-Api-Key)
 */
@ConfigurationProperties(prefix = "ai.internal")
public record AiClientProperties(
        String baseUrl,
        String apiKey
) {
}
