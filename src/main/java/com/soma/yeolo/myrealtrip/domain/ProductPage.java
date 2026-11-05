package com.soma.yeolo.myrealtrip.domain;

import java.util.List;

/**
 * 상품 목록 한 페이지 (API-MRT-3/4 §상품 및 페이지 필드). 페이지 크기는 {@link #PAGE_SIZE}로 고정이다.
 *
 * <p>{@code hasMore}는 상위 API 가 전체 건수를 주면 그것으로, 주지 않으면 "필터링 전 원본 페이지가 가득 찼는가"로
 * 판단한다 — 가짜 전체 건수를 만들지 않는다(명세). 그래서 {@code totalCount}는 null 일 수 있고, 그때 Client 는
 * 전체 건수 표시를 생략한다.
 *
 * @param items      유효한 상품만. 결과 없음은 빈 목록
 * @param page       Client 가 요청한 1부터 시작하는 페이지
 * @param totalCount 상위 API 가 준 전체 건수. 미제공 시 null
 * @param hasMore    다음 페이지가 있는가
 */
public record ProductPage(List<Product> items, int page, Long totalCount, boolean hasMore) {

    public static final int PAGE_SIZE = 20;

    /**
     * @param items         필터링 후 상품
     * @param page          1 부터 시작하는 요청 페이지
     * @param upstreamTotal 상위 API 전체 건수(없거나 음수·비정수면 null 로 넘긴다)
     * @param rawCount      필터링 전 원본 페이지 항목 수 — hasMore 판단은 이것으로 한다
     */
    public static ProductPage of(List<Product> items, int page, Long upstreamTotal, int rawCount) {
        if (upstreamTotal != null) {
            return new ProductPage(items, page, upstreamTotal, (long) page * PAGE_SIZE < upstreamTotal);
        }
        return new ProductPage(items, page, null, rawCount >= PAGE_SIZE);
    }
}
