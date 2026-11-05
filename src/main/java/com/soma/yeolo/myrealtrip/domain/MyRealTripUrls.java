package com.soma.yeolo.myrealtrip.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * 마이리얼트립 URL 검증 (API-MRT-3/4 §상품 필드, API-MRT-5/6 §처리 규칙). 문자열 포함 검사가 아니라 URL 파서로
 * 본다 — {@code myrealtrip.com.evil.example} 같은 유사 도메인, HTTP, 사용자정보·명시적 포트가 있는 URL 은 거절한다.
 * 이관 전 FE 어댑터의 {@code isMyRealTripUrl}과 같은 규칙이다. 파서는 {@link URI}(RFC 2396) 라 브라우저의 WHATWG
 * {@code URL} 보다 엄격하다 — 공백·{@code |}·{@code {} 같은 비인코딩 문자가 든 URL 은 거절된다. 그런 URL 은 제휴 링크
 * 변환(API-MRT-6)에도 못 쓰므로 상품 단계에서 걸러도 손해가 없고, 어댑터가 제외 건수를 로그로 남긴다.
 */
public final class MyRealTripUrls {

    private static final String PRODUCT_HOST = "myrealtrip.com";

    private MyRealTripUrls() {
    }

    /** 상품 원본 URL — HTTPS 이고 호스트가 정확히 {@code myrealtrip.com} 이거나 그 서브도메인이어야 한다. */
    public static boolean isProductUrl(String value) {
        String host = secureHost(value);
        return host != null && (host.equals(PRODUCT_HOST) || host.endsWith("." + PRODUCT_HOST));
    }

    /** HTTPS·사용자정보 없음·명시적 포트 없음이면 소문자 호스트, 아니면 null. */
    private static String secureHost(String value) {
        if (value == null) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawUserInfo() != null || uri.getPort() != -1) {
            return null;
        }
        return uri.getHost().toLowerCase(Locale.ROOT);
    }
}
