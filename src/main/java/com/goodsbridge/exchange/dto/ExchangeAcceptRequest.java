package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 교환 수락 요청 (POST /api/exchange-requests/{id}/accept) - A 보증금 결제 검증용
@Getter
@NoArgsConstructor
public class ExchangeAcceptRequest {

    private String paymentId;

    public void validate() {
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("결제 정보가 확인되지 않습니다.");
        }
    }
}
