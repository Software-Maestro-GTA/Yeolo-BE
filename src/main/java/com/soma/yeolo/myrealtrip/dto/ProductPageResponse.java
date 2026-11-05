package com.soma.yeolo.myrealtrip.dto;

import com.soma.yeolo.myrealtrip.domain.Product;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import java.util.List;
import java.util.function.Function;

/**
 * 상품 목록 API 의 공통 페이지 틀 (API-MRT-3 숙소 / API-MRT-4 투어·티켓 §상품 및 페이지 필드). 항목 타입만 다르고
 * {@code items/page/totalCount/hasMore} 규칙은 같으므로 한 곳에 둔다 — 명세 JSON 스키마는 엔드포인트별 항목 record 로
 * 명시한다({@link HotelSearchResponse.Item}, {@link TicketSearchResponse.Item}).
 *
 * @param items      유효한 상품만. 결과 없음은 빈 배열
 * @param page       Client 가 요청한 1부터 시작하는 페이지
 * @param totalCount 상위 API 가 준 전체 건수. 미제공 시 생략이 아니라 명시적 {@code null}(Client 는 전체 건수 표시를 생략)
 * @param hasMore    다음 페이지 존재 여부
 */
public record ProductPageResponse<I>(List<I> items, int page, Long totalCount, boolean hasMore) {

    public static <I> ProductPageResponse<I> from(ProductPage page, Function<Product, I> item) {
        return new ProductPageResponse<>(page.items().stream().map(item).toList(),
                page.page(), page.totalCount(), page.hasMore());
    }
}
