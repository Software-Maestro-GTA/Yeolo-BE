package com.soma.yeolo.share.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 공유 링크 설정의 만료·URL 조립 규칙 (API-SHARE-1). */
class ShareLinkPropertiesTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-08-10T12:00:00Z");

    @Test
    void ttl만큼_뒤가_만료_시각이다() {
        ShareLinkProperties properties =
                new ShareLinkProperties("https://yeolo.vercel.app/invite", Duration.ofDays(7));

        assertThat(properties.expiresAtFrom(ISSUED_AT)).isEqualTo(Instant.parse("2026-08-17T12:00:00Z"));
    }

    /** 명세의 {@code expiresAt|null}(무기한)을 설정으로 표현하는 방법이다. */
    @Test
    void ttl이_0이하거나_없으면_무기한이다() {
        assertThat(new ShareLinkProperties("https://x", Duration.ZERO).expiresAtFrom(ISSUED_AT)).isNull();
        assertThat(new ShareLinkProperties("https://x", Duration.ofDays(-1)).expiresAtFrom(ISSUED_AT))
                .isNull();
        assertThat(new ShareLinkProperties("https://x", null).expiresAtFrom(ISSUED_AT)).isNull();
    }

    @Test
    void 기준_URL_뒤에_토큰을_붙인다() {
        ShareLinkProperties properties =
                new ShareLinkProperties("https://yeolo.vercel.app/invite", Duration.ofDays(7));

        assertThat(properties.shareUrl("abc123")).isEqualTo("https://yeolo.vercel.app/invite/abc123");
    }

    /** 환경변수로 주입되는 값이라 끝 슬래시가 붙어 오기 쉽다 — 슬래시가 겹치지 않아야 한다. */
    @Test
    void 기준_URL의_끝_슬래시는_무시한다() {
        ShareLinkProperties properties =
                new ShareLinkProperties("https://yeolo.vercel.app/invite//", Duration.ofDays(7));

        assertThat(properties.shareUrl("abc123")).isEqualTo("https://yeolo.vercel.app/invite/abc123");
    }

    /**
     * 배포 기본값의 <b>경로</b>를 고정한다. 기준 URL 은 FE 초대 페이지 라우트와 정확히 같아야
     * 하며, 현재 FE 라우트는 {@code /invite/[shareToken]} 이다.
     *
     * <p>이 검증이 없으면 값이 옛 {@code /share} 로 되돌아가도 <b>테스트는 전부 초록인 채</b>
     * 발급된 링크만 FE 404 로 떨어진다 — BE 는 200 을 내고 판정 주체가 브라우저뿐이라 실패가
     * 런타임까지 밀린다. 위 테스트들은 모두 자기 fixture 로 조립 규칙만 보므로, 실제 설정값을
     * 지키는 것은 이 테스트뿐이다. (CLAUDE.md §코드 컨벤션 — {@code budgetType} 리버트를 놓친
     * 전례와 같은 이유)
     *
     * <p>도메인 교체는 자유롭지만 경로는 FE 와 함께 바꿔야 한다. 그래서 호스트가 아니라 경로를
     * 검증한다.
     */
    @Test
    void 배포_기본값은_FE_초대_페이지_경로를_가리킨다() throws IOException {
        assertThat(configuredBaseUrl())
                .as("FE 라우트가 /invite/[shareToken] 이므로 기준 URL 도 /invite 로 끝나야 한다")
                .endsWith("/invite}");
    }

    /** 명세 개정이 아니라 오설정으로 되돌아가기 쉬운 옛 경로를 못 박아 둔다. */
    @Test
    void 배포_기본값은_FE에_없는_옛_경로_share를_쓰지_않는다() throws IOException {
        assertThat(configuredBaseUrl())
                .as("/share 는 FE 에 없는 경로다 — 그 값으로 발급된 링크는 전부 FE 404 다")
                .doesNotContain("/share");
    }

    /**
     * 배포에 실제로 실리는 {@code application.properties} 의 설정 줄을 읽는다.
     *
     * <p>테스트 클래스패스에는 {@code src/test/resources} 의 동명 파일이 앞서 잡혀 클래스패스
     * 조회로는 배포본을 볼 수 없다. 검증 대상이 "배포되는 그 파일"이므로 경로로 직접 읽는다
     * (Gradle Test 의 작업 디렉터리는 프로젝트 루트다).
     */
    private static String configuredBaseUrl() throws IOException {
        String key = "share-link.base-url=";
        return Files.readAllLines(Path.of("src/main/resources/application.properties")).stream()
                .filter(line -> line.startsWith(key))
                .findFirst()
                .map(line -> line.substring(key.length()))
                .orElseThrow(() -> new AssertionError(key + " 설정이 application.properties 에 없다"));
    }
}
