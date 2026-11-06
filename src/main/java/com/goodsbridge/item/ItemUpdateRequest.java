package com.goodsbridge.item;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ItemUpdateRequest {
    private Long categoryId;
    private String title;
    private String description;
    private Integer price;
    private SaleType saleType;
    private ItemStatus status;
    private String imageUrl;

    public void validate() {
        if (categoryId == null) {
            throw new IllegalArgumentException("카테고리 ID는 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("상품 제목은 필수입니다.");
        }
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("가격은 0보다 커야 합니다.");
        }
        if (saleType == null) {
            throw new IllegalArgumentException("판매 타입은 필수입니다.");
        }
    }
}
