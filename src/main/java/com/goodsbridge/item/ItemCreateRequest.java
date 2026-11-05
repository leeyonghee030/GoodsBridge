package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class ItemCreateRequest {
    private Long categoryId;
    private Long shopId;
    private String title;
    private String description;
    private Integer price;
    private SaleType saleType;
    private String imageUrl;

    public void validate() {
        if (categoryId == null) {
            throw new BadRequestException("카테고리 ID는 필수입니다.");
        }
        if (title == null || title.isBlank()) {
            throw new BadRequestException("상품 제목은 필수입니다.");
        }
        if (price == null || price <= 0) {
            throw new BadRequestException("가격은 0보다 커야 합니다.");
        }
        if (saleType == null) {
            throw new BadRequestException("판매 타입은 필수입니다.");
        }
    }
}
