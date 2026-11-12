package com.goodsbridge.shop;

import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ShopResponse {
    private final Long id;
    private final Long ownerUserId;
    private final String name;
    private final String region;
    private final String address;
    private final ShopStatus status;
    private final LocalDateTime createdAt;

    public ShopResponse(Shop shop) {
        this.id = shop.getId();
        this.ownerUserId = shop.getOwnerUserId();
        this.name = shop.getName();
        this.region = shop.getRegion();
        this.address = shop.getAddress();
        this.status = shop.getStatus();
        this.createdAt = shop.getCreatedAt();
    }
}
