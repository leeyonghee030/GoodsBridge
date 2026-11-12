package com.goodsbridge.shop;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShopSubscriptionActivatedEvent {
    private final Long shopId;
}
