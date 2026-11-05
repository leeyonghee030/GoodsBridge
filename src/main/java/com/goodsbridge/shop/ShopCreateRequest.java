package com.goodsbridge.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopCreateRequest {
    private String name;
    private String region;
    private String address;


    public void validate() {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("가게 이름은 필수 입력 값입니다.");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("가게 이름은 50자 이내여야 합니다.");
        }

        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("지역은 필수 입력 값입니다.");
        }

        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("주소는 필수 입력 값입니다.");
        }
    }
}
