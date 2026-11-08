package com.soma.yeolo.myrealtrip.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 마이리얼트립 마케팅파트너 Open API 연동 설정 (API-MRT-1~6 §서버 연동 및 근거).
 *
 * <p>파트너 키는 BE 환경변수({@code MY_REAL_TRIP_API_KEY})로만 보관하며 커밋하지 않는다. 키가 비어 있으면
 * 호출 없이 500(파트너 키 설정 누락)이다. 파트너 API 는 등록된 공인 IP 에서만 호출할 수 있으므로 키가 맞아도
 * 송신 IP 가 미등록이면 파트너가 401/403 을 돌려주고 BE 는 이를 502 로 낸다.
 *
 * <p>요청 한도는 사용자(토큰의 userId) 단위 고정 창이다. 명세는 "사용자별 요청 제한을 적용한다"고만 하고
 * 수치를 주지 않아 설정으로 둔다 — 자동완성은 입력마다 호출되므로(FE 250ms 디바운스) 분당 수십 회가 정상이다.
 *
 * @param baseUrl              파트너 호스트 (https://partner-ext-api.myrealtrip.com)
 * @param apiKey               파트너 API 키. {@code Authorization: Bearer} 로 파트너에만 전달한다.
 * @param connectTimeoutMs     연결 타임아웃(ms)
 * @param readTimeoutMs        응답 타임아웃(ms). 초과하면 504.
 * @param rateLimitMaxRequests 창 하나에서 사용자당 허용 요청 수
 * @param rateLimitWindow      요청 제한 창 길이
 */
@ConfigurationProperties(prefix = "myrealtrip")
public record MyRealTripProperties(
        String baseUrl,
        String apiKey,
        long connectTimeoutMs,
        long readTimeoutMs,
        int rateLimitMaxRequests,
        Duration rateLimitWindow
) {
}
