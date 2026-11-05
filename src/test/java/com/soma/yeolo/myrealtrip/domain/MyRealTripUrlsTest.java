package com.soma.yeolo.myrealtrip.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** URL 파서 기반 검증 — 유사 도메인·HTTP·사용자정보·명시적 포트를 거절한다(API-MRT-3/4 §상품 필드, API-MRT-6 §처리 규칙). */
class MyRealTripUrlsTest {

    @Test
    void myrealtrip_com_과_그_서브도메인의_HTTPS_만_상품_URL_이다() {
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com/products/1")).isTrue();
        assertThat(MyRealTripUrls.isProductUrl("https://experiences.myrealtrip.com/products/example")).isTrue();
        assertThat(MyRealTripUrls.isProductUrl("https://accommodation.myrealtrip.com/union/products/example?x=1")).isTrue();
        assertThat(MyRealTripUrls.isProductUrl("https://WWW.MyRealTrip.com/")).isTrue();
    }

    @Test
    void 유사_도메인_HTTP_사용자정보_포트_빈값은_거절한다() {
        assertThat(MyRealTripUrls.isProductUrl("http://myrealtrip.com/products/1")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com.evil.example/products/1")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://evilmyrealtrip.com/")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://user:pw@myrealtrip.com/")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com:8443/")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealt.rip/abc")).isFalse();   // 제휴 단축 링크는 상품 URL 이 아니다
        assertThat(MyRealTripUrls.isProductUrl("myrealtrip.com/products/1")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("not a url")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl(null)).isFalse();
    }
}
