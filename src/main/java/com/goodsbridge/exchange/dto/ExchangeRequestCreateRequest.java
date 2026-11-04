package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ErrorCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 교환 제안 요청 (POST /api/exchange-items/{id}/propose)
@Getter
@NoArgsConstructor
public class ExchangeRequestCreateRequest {

    private Long itemBId;  // 제안자(B)가 제안에 쓸 본인 소유 Item id
    private String message; // A에게 보내는 짧은 제안 메시지 (nullable)
    private String paymentId;

    public void validate() {
        if (itemBId == null) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }
        if (message == null || message.isBlank()) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }
    }
}
