package com.goodsbridge.exchange.dto;

import com.goodsbridge.exchange.ExchangeItem;
import com.goodsbridge.exchange.ExchangeItemStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 교환 게시글 응답
@Getter
@NoArgsConstructor
public class ExchangeItemResponse {

    private Long id;
    private Long userId;
    private Long itemId;
    private String itemTitle;
    private Integer itemPrice;
    private String itemImageUrl;
    private String wantDescription;
    private Long shopId;
    private String shopName;
    private ExchangeItemStatus status;
    private LocalDateTime createdAt;

    @Builder
    public ExchangeItemResponse(Long id, Long userId, Long itemId, String itemTitle, Integer itemPrice, String itemImageUrl,
                                 String wantDescription, Long shopId, String shopName,
                                 ExchangeItemStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.itemId = itemId;
        this.itemTitle = itemTitle;
        this.itemPrice = itemPrice;
        this.itemImageUrl = itemImageUrl;
        this.wantDescription = wantDescription;
        this.shopId = shopId;
        this.shopName = shopName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ExchangeItemResponse from(ExchangeItem exchangeItem) {
        return ExchangeItemResponse.builder()
            .id(exchangeItem.getId())
            .userId(exchangeItem.getUser().getId())
            .itemId(exchangeItem.getItem().getId())
            .itemTitle(exchangeItem.getItem().getTitle())
            .itemPrice(exchangeItem.getItem().getPrice())
            .itemImageUrl(exchangeItem.getItem().getImageUrl())
            .wantDescription(exchangeItem.getWantDescription())
            .shopId(exchangeItem.getShop().getId())
            .shopName(exchangeItem.getShop().getName())
            .status(exchangeItem.getStatus())
            .createdAt(exchangeItem.getCreatedAt())
            .build();
    }
}
