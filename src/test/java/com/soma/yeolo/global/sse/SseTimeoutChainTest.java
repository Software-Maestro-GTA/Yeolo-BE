package com.soma.yeolo.global.sse;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * 타임아웃 체인 불변식을 지킨다.
 *
 * <p>SSE 파이프라인은 구조적으로 길다(지오코딩 직렬 반복 + AI 80초대). 여기서 값 하나가 어긋나면
 * "정상적으로 긴 요청"이 실패한다 — 안쪽(AI read)이 바깥쪽(SSE stream)보다 길면 사용자는
 * {@code error} 이벤트조차 못 받고 연결만 끊긴다. 상수가 아니라 설정으로 뺐으니 순서는 테스트로 잠근다.
 */
class SseTimeoutChainTest {

    /** {@code ${ENV_VAR:default}}에서 default를 뽑는다. 운영 기본값이 검증 대상이다. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^:}]+:([^}]*)}");

    private static final Path APPLICATION_PROPERTIES =
            Path.of("src/main/resources/application.properties");

    @Test
    void AI_read_타임아웃은_SSE_스트림_상한보다_짧다() throws IOException {
        long aiReadTimeout = longValue("ai.internal.read-timeout-ms");
        long sseStreamTimeout = longValue("sse.stream-timeout-ms");

        assertThat(aiReadTimeout).isLessThan(sseStreamTimeout);
    }

    @Test
    void SSE_상한은_실측_AI_분석_시간보다_충분히_길다() throws IOException {
        // 실측 AI 분석 80초대 + 이미지당 지오코딩(정책상 ≥1초 간격) + 후처리.
        // 여기에 걸릴 정도면 값이 아니라 파이프라인을 다시 봐야 한다.
        assertThat(longValue("sse.stream-timeout-ms")).isGreaterThanOrEqualTo(300_000L);
    }

    private long longValue(String key) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(APPLICATION_PROPERTIES)) {
            properties.load(reader);
        }
        String raw = properties.getProperty(key);
        assertThat(raw).as("%s 설정이 있어야 한다", key).isNotNull();

        Matcher matcher = PLACEHOLDER.matcher(raw);
        return Long.parseLong(matcher.matches() ? matcher.group(1) : raw);
    }
}
