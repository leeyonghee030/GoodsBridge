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
    private Long itemId;
    private String wantDescription;
    private Long shopId;
    private ExchangeItemStatus status;
    private LocalDateTime createdAt;

    @Builder
    public ExchangeItemResponse(Long id, Long itemId, String wantDescription, Long shopId, ExchangeItemStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.itemId = itemId;
        this.wantDescription = wantDescription;
        this.shopId = shopId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ExchangeItemResponse from(ExchangeItem exchangeItem) {
        return ExchangeItemResponse.builder()
            .id(exchangeItem.getId())
            .itemId(exchangeItem.getItem().getId())
            .wantDescription(exchangeItem.getWantDescription())
            .shopId(exchangeItem.getShop().getId())
            .status(exchangeItem.getStatus())
            .createdAt(exchangeItem.getCreatedAt())
            .build();
    }
}
