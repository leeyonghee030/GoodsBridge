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
    private Long shopId;
    private Integer capacity;
    private Integer currentCount;
    private Integer feeAmount;
    private LocalDateTime deadline;
    private ShopEventStatus status;
    private LocalDateTime createdAt;

    @Builder
    public ShopEventResponse(Long id, Long itemId, Long shopId, Integer capacity, Integer currentCount,
                              Integer feeAmount, LocalDateTime deadline, ShopEventStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.itemId = itemId;
        this.shopId = shopId;
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
            .shopId(shopEvent.getShop().getId())
            .capacity(shopEvent.getCapacity())
            .currentCount(shopEvent.getCurrentCount())
            .feeAmount(shopEvent.getFeeAmount())
            .deadline(shopEvent.getDeadline())
            .status(shopEvent.getStatus())
            .createdAt(shopEvent.getCreatedAt())
            .build();
    }
}
