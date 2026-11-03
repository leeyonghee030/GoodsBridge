package com.goodsbridge.item;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.sql.Timestamp;


@Getter
@AllArgsConstructor
public class ItemResponse {
    private Long id;
    private Long sellerUserId;
    private Long shopId;
    private String categoryName;
    private String title;
    private String description;
    private Integer price;
    private SaleType saleType;
    private ItemStatus status;
    private String imageUrl;
    private Timestamp createdAt;

    public static ItemResponse from(Item item) {
        return new ItemResponse(
            item.getId(),
            item.getSellerUserId(),
            item.getShopId(),
            item.getCategory().getName(),
            item.getTitle(),
            item.getDescription(),
            item.getPrice(),
            item.getSaleType(),
            item.getStatus(),
            item.getImageUrl(),
            item.getCreatedAt()
        );
    }
}
