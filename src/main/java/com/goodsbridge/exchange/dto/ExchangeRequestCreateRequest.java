package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
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
            throw new BadRequestException("아이템을 지정해 주세요.");
        }
        if (message == null || message.isBlank()) {
            throw new BadRequestException("제안 메시지가 필요합니다.");
        }
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("결제 정보가 확인되지 않습니다.");
        }
    }
}
