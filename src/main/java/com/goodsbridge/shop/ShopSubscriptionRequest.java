package com.goodsbridge.shop;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
public class ShopSubscriptionRequest {
    private String paymentId;
    private Integer amount;
    private Integer days; // 구독할 일수 (예: 30, 90, 365)
}
