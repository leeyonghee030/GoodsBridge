package com.goodsbridge.event.dto;

import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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
    private Integer maxPerUser;
    private LocalDateTime deadline;
    private LocalDate visitUntil;       // 손님 방문 마감일 (이 날이 지나야 종료)
    private ShopEventStatus status;
    private LocalDateTime createdAt;

    @Builder
    public ShopEventResponse(Long id, Long itemId, String itemTitle, String itemImageUrl, Integer itemPrice, String itemDescription,
                              Long shopId, String shopName, String shopAddress,
                              Integer capacity, Integer currentCount,
                              Integer feeAmount, Integer maxPerUser, LocalDateTime deadline, LocalDate visitUntil, ShopEventStatus status, LocalDateTime createdAt) {
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
        this.maxPerUser = maxPerUser;
        this.deadline = deadline;
        this.visitUntil = visitUntil;
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
            .maxPerUser(shopEvent.getMaxPerUser())
            .deadline(shopEvent.getDeadline())
            .visitUntil(shopEvent.getVisitUntil())
            .status(shopEvent.getStatus())
            .createdAt(shopEvent.getCreatedAt())
            .build();
    }
}
