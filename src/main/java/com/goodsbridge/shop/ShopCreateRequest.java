package com.goodsbridge.shop;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShopCreateRequest {
    private String name;
    private String region;
    private String address;
}
