package com.goodsbridge.item;

import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
