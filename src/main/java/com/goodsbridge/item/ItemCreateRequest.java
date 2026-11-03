package com.goodsbridge.item;

import lombok.Getter;
import lombok.NoArgsConstructor;

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
}
