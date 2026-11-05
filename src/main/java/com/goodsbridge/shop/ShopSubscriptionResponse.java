package com.goodsbridge.shop;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class ShopSubscriptionResponse {
    private Long id;
    private Long shopId;
    private boolean setupPaid;
    private LocalDate startDate;
    private LocalDate endDate;
    private ShopSubscriptionStatus status;
    private LocalDateTime createdAt;

    public static ShopSubscriptionResponse from(ShopSubscription shopSubscription) {
        return new ShopSubscriptionResponse(
            shopSubscription.getId(),
            shopSubscription.getShopId(),
            shopSubscription.getSetupFeePaid(),
            shopSubscription.getStartDate(),
            shopSubscription.getEndDate(),
            shopSubscription.getStatus(),
            shopSubscription.getCreatedAt()
            );

    }
}
