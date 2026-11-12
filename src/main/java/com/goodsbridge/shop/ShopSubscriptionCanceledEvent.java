package com.goodsbridge.shop;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShopSubscriptionCanceledEvent {
    private final Long shopId;
}
