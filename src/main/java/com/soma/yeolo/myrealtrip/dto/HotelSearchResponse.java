package com.soma.yeolo.myrealtrip.dto;

import com.soma.yeolo.myrealtrip.domain.Product;
import com.soma.yeolo.myrealtrip.domain.ProductPage;
import java.math.BigDecimal;

/**
 * API-MRT-3 응답 {@code data} 항목. 페이지 틀은 {@link ProductPageResponse}. Client 가 쓰지 않는 category·tags·priceDisplay 는 없다.
 *
 * @param reviewScore 공급자가 문자열 또는 숫자로 주며 명세가 "현재 Client 타입 유지"라 그대로 넘긴다({@code string | number | null})
 */
public final class HotelSearchResponse {

    private HotelSearchResponse() {
    }

    public static ProductPageResponse<Item> from(ProductPage page) {
        return ProductPageResponse.from(page, Item::from);
    }

    public record Item(String itemName, String productUrl, BigDecimal salePrice, String imageUrl,
                       BigDecimal starRating, Object reviewScore, Integer reviewCount) {

        static Item from(Product p) {
            return new Item(p.itemName(), p.productUrl(), p.salePrice(), p.imageUrl(),
                    p.starRating(), p.reviewScore(), p.reviewCount());
        }
    }
}
