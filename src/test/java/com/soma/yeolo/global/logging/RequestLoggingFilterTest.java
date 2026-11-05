package com.soma.yeolo.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/** 접근 로그의 {@code X-App-Version} 처리 — 없으면 생략, 있으면 정리해서 남긴다(로그 인젝션·길이 방어). */
class RequestLoggingFilterTest {

    @Test
    void 헤더가_없거나_비어_있으면_null이다() {
        assertThat(RequestLoggingFilter.appVersion(new MockHttpServletRequest())).isNull();
        assertThat(RequestLoggingFilter.appVersion(withVersion("   "))).isNull();
        assertThat(RequestLoggingFilter.appVersion(withVersion("\n\t"))).isNull();
    }

    @Test
    void 정상_버전은_그대로_남긴다() {
        assertThat(RequestLoggingFilter.appVersion(withVersion("1.4.2"))).isEqualTo("1.4.2");
        assertThat(RequestLoggingFilter.appVersion(withVersion(" 1.4.2 (build 88) "))).isEqualTo("1.4.2 (build 88)");
    }

    @Test
    void 허용_문자_밖은_지우고_길이는_자른다() {
        // 개행·유니코드 줄 구분자(U+2028)·NEL(U+0085)로 가짜 로그 줄을 만들 수 없다.
        assertThat(RequestLoggingFilter.appVersion(withVersion("1.0\n<-- GET /x 200"))).isEqualTo("1.0 -- GET x 200");
        assertThat(RequestLoggingFilter.appVersion(withVersion("1.0\u2028<-- GET /x 200"))).isEqualTo("1.0 -- GET x 200");
        assertThat(RequestLoggingFilter.appVersion(withVersion("1.0\u0085<-- GET /x 200"))).isEqualTo("1.0 -- GET x 200");
        assertThat(RequestLoggingFilter.appVersion(withVersion("v".repeat(100)))).hasSize(32);
    }

    private static MockHttpServletRequest withVersion(String value) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestLoggingFilter.APP_VERSION_HEADER, value);
        return request;
    }
}
