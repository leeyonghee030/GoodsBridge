package com.goodsbridge.shop;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShopSubscriptionExpiredEvent {
    private final Long shopId;
}
