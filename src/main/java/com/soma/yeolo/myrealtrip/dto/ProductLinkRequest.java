package com.soma.yeolo.myrealtrip.dto;

/** API-MRT-6 요청 본문. {@code targetUrl}은 검색 응답의 {@code productUrl} 또는 마이리얼트립 홈이다. */
public record ProductLinkRequest(String targetUrl) {
}
