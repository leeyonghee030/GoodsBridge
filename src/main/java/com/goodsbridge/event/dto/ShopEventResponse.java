package com.goodsbridge.event.dto;

import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ShopEventResponse {

    private Long id;
    private Long itemId;
    private String itemTitle;
    private String itemImageUrl;
    private Integer itemPrice;          // 이벤트 상품 가격 (번호표를 받고 매장에서 현장 구매하는 가격)
    private String itemDescription;
    private Long shopId;
    private String shopName;
    private String shopAddress;
    private Integer capacity;
    private Integer currentCount;
    private Integer feeAmount;
    private LocalDateTime deadline;
    private ShopEventStatus status;
    private LocalDateTime createdAt;

    @Builder
    public ShopEventResponse(Long id, Long itemId, String itemTitle, String itemImageUrl, Integer itemPrice, String itemDescription,
                              Long shopId, String shopName, String shopAddress,
                              Integer capacity, Integer currentCount,
                              Integer feeAmount, LocalDateTime deadline, ShopEventStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.itemId = itemId;
        this.itemTitle = itemTitle;
        this.itemImageUrl = itemImageUrl;
        this.itemPrice = itemPrice;
        this.itemDescription = itemDescription;
        this.shopId = shopId;
        this.shopName = shopName;
        this.shopAddress = shopAddress;
        this.capacity = capacity;
        this.currentCount = currentCount;
        this.feeAmount = feeAmount;
        this.deadline = deadline;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static ShopEventResponse from(ShopEvent shopEvent) {
        return ShopEventResponse.builder()
            .id(shopEvent.getId())
            .itemId(shopEvent.getItem().getId())
            .itemTitle(shopEvent.getItem().getTitle())
            .itemImageUrl(shopEvent.getItem().getImageUrl())
            .itemPrice(shopEvent.getItem().getPrice())
            .itemDescription(shopEvent.getItem().getDescription())
            .shopId(shopEvent.getShop().getId())
            .shopName(shopEvent.getShop().getName())
            .shopAddress(shopEvent.getShop().getAddress())
            .capacity(shopEvent.getCapacity())
            .currentCount(shopEvent.getCurrentCount())
            .feeAmount(shopEvent.getFeeAmount())
            .deadline(shopEvent.getDeadline())
            .status(shopEvent.getStatus())
            .createdAt(shopEvent.getCreatedAt())
            .build();
    }
}
