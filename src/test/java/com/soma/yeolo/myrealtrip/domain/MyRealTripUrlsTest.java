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
        assertThat(MyRealTripUrls.isProductUrl("HTTPS://myrealtrip.com")).isTrue();   // 경로 없음·대문자 스킴
        // 경로·쿼리의 비인코딩 한글·공백·| 는 판정과 무관하다 — 공급자 랜딩 URL 이 그렇게 온다.
        assertThat(MyRealTripUrls.isProductUrl("https://flights.myrealtrip.com/search?dep=ICN&depNm=인천&q=a|b c")).isTrue();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com/products/상품 이름#frag")).isTrue();
    }

    @Test
    void 호스트_뒤에_붙는_것은_슬래시_물음표_샵_뿐이고_authority_의_다른_문자는_거절한다() {
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com?x=1")).isTrue();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com#x")).isTrue();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com\\evil.example/")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com%2Fevil.example/")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com./")).isFalse();     // 끝 점
        assertThat(MyRealTripUrls.isProductUrl("https://.myrealtrip.com/")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://my realtrip.com/")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://[::1]/")).isFalse();
    }

    @Test
    void 경로_쿼리에_제어_문자가_있으면_거절한다() {
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com/products/1\n<script>")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com/p?q=a\tb")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com/p\r")).isFalse();
        assertThat(MyRealTripUrls.isProductUrl("https://myrealtrip.com/p\u007f")).isFalse();
        assertThat(MyRealTripUrls.isShortLink("https://myrealt.rip/x\n")).isFalse();
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

    @Test
    void 제휴_단축_링크는_HTTPS의_myrealt_rip_호스트_정확히_하나만이다() {
        assertThat(MyRealTripUrls.isShortLink("https://myrealt.rip/example")).isTrue();
        assertThat(MyRealTripUrls.isShortLink("https://MYREALT.RIP/x?y=1")).isTrue();
        assertThat(MyRealTripUrls.isShortLink("http://myrealt.rip/example")).isFalse();
        assertThat(MyRealTripUrls.isShortLink("https://www.myrealt.rip/example")).isFalse();   // 서브도메인 불허
        assertThat(MyRealTripUrls.isShortLink("https://myrealt.rip.evil.example/")).isFalse();
        assertThat(MyRealTripUrls.isShortLink("https://myrealt.rip:443/")).isFalse();
        assertThat(MyRealTripUrls.isShortLink("https://myrealtrip.com/products/1")).isFalse();  // 상품 URL 은 단축 링크가 아니다
        assertThat(MyRealTripUrls.isShortLink(null)).isFalse();
    }
}
