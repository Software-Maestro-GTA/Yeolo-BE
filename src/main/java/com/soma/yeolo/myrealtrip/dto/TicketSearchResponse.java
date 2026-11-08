package com.soma.yeolo.myrealtrip.dto;

import com.soma.yeolo.myrealtrip.domain.Product;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import java.math.BigDecimal;

/**
 * API-MRT-4 응답 {@code data} 항목. 숙소와 같은 페이지 틀({@link ProductPageResponse})이고 {@code starRating} 대신
 * {@code description}이 있다. 가격은 상품 시작 가격이며 특정 날짜·옵션의 확정 가격이 아니다.
 */
public final class TicketSearchResponse {

    private TicketSearchResponse() {
    }

    public static ProductPageResponse<Item> from(ProductPage page) {
        return ProductPageResponse.from(page, Item::from);
    }

    public record Item(String itemName, String productUrl, BigDecimal salePrice, String imageUrl,
                       String description, Object reviewScore, Integer reviewCount) {

        static Item from(Product p) {
            return new Item(p.itemName(), p.productUrl(), p.salePrice(), p.imageUrl(),
                    p.description(), p.reviewScore(), p.reviewCount());
        }
    }
}
