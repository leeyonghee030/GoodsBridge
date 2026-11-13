package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
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
    private String chainCode; // 체인점 코드 (본사가 지점에 알려주는 값, ShopService.create 에서 검증)


    public void validate() {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("가게 이름은 필수 입력 값입니다.");
        }
        if (name.length() > 50) {
            throw new BadRequestException("가게 이름은 50자 이내여야 합니다.");
        }

        if (region == null || region.isBlank()) {
            throw new BadRequestException("지역은 필수 입력 값입니다.");
        }

        if (address == null || address.isBlank()) {
            throw new BadRequestException("주소는 필수 입력 값입니다.");
        }
    }
}
