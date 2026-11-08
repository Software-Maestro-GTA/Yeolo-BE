package com.soma.yeolo.myrealtrip.domain;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 마이리얼트립 URL 검증 (API-MRT-3/4 §상품 필드, API-MRT-5/6 §처리 규칙). 문자열 포함 검사가 아니라 URL 을 분해해서 본다 —
 * {@code myrealtrip.com.evil.example} 같은 유사 도메인, HTTP, 사용자정보·명시적 포트가 있는 URL 은 거절한다.
 * 이관 전 FE 어댑터의 {@code isMyRealTripUrl}(WHATWG {@code URL})과 같은 규칙이다.
 *
 * <p>{@link java.net.URI}(RFC 2396)를 쓰지 않는 이유: 공급자 URL 의 경로·쿼리에 한글·공백·{@code |} 같은 비인코딩 문자가
 * 섞여 올 수 있고(예: {@code ?depNm=인천}), 브라우저는 그것을 열지만 {@code URI}는 예외를 던져 멀쩡한 상품·랜딩 URL 을
 * 통째로 버리게 된다. 판정에 필요한 것은 스킴과 authority 뿐이므로 그 둘만 엄격히 자르고, 나머지는 제어 문자(개행·탭 등,
 * 헤더·로그 인젝션과 링크 변조의 재료)가 없는지만 본다.
 */
public final class MyRealTripUrls {

    private static final String PRODUCT_HOST = "myrealtrip.com";
    private static final String SHORT_LINK_HOST = "myrealt.rip";
    private static final String HTTPS_PREFIX = "https://";
    /** 호스트는 소문자·숫자·점·하이픈만. 사용자정보({@code @})·포트({@code :})·IPv6 괄호는 여기서 걸러진다. */
    private static final Pattern HOST = Pattern.compile("[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+");

    private MyRealTripUrls() {
    }

    /** 상품 원본·랜딩 URL — HTTPS 이고 호스트가 정확히 {@code myrealtrip.com} 이거나 그 서브도메인이어야 한다. */
    public static boolean isProductUrl(String value) {
        String host = secureHost(value);
        return host != null && (host.equals(PRODUCT_HOST) || host.endsWith("." + PRODUCT_HOST));
    }

    /** 제휴 단축 링크(mylink 결과) — HTTPS 이고 호스트가 정확히 {@code myrealt.rip} 이어야 한다(API-MRT-5/6 §처리 규칙). */
    public static boolean isShortLink(String value) {
        return SHORT_LINK_HOST.equals(secureHost(value));
    }

    /**
     * HTTPS 이고 authority 가 순수 호스트(사용자정보·포트 없음)이면 소문자 호스트, 아니면 null.
     * authority 는 {@code https://} 다음부터 첫 {@code / ? #} 전까지다.
     */
    private static String secureHost(String value) {
        if (value == null || value.length() <= HTTPS_PREFIX.length()
                || !value.regionMatches(true, 0, HTTPS_PREFIX, 0, HTTPS_PREFIX.length())
                || hasControlCharacter(value)) {
            return null;
        }
        int start = HTTPS_PREFIX.length();
        int end = value.length();
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String host = value.substring(start, end).toLowerCase(Locale.ROOT);
        return HOST.matcher(host).matches() ? host : null;
    }

    /** C0 제어 문자(0x00–0x1F)와 DEL. 공백은 경로·쿼리에서 허용한다(브라우저가 인코딩해 연다). */
    private static boolean hasControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                return true;
            }
        }
        return false;
    }
}
