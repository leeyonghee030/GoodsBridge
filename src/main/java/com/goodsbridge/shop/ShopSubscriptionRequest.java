package com.goodsbridge.shop;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
public class ShopSubscriptionRequest {
    private String paymentId;
    private Integer amount;
    private Integer days; // 구독할 일수 (예: 30, 90, 365)
}
