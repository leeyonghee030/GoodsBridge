package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 결제 전 신청 가능 여부 확인 (POST /api/exchange-items/{id}/propose/check)
@Getter
@NoArgsConstructor
public class ExchangeProposeCheckRequest {

    private Long itemBId;

    public void validate() {
        if (itemBId == null) {
            throw new BadRequestException("아이템을 지정해 주세요.");
        }
    }
}
