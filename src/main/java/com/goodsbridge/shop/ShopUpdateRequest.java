package com.goodsbridge.shop;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ShopUpdateRequest {
    private String name;
    private String region;
    private String address;
}
