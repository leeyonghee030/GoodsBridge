package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 가게 체크인 요청 (PATCH /api/shops/{shopId}/exchange-requests/{id}/check-in)
@Getter
@NoArgsConstructor
public class ExchangeCheckInRequest {

    private Boolean userAShowed; // null이면 변경 안 함
    private Boolean userBShowed; // null이면 변경 안 함

    public void validate() {
        if (userAShowed == null && userBShowed == null) {
            throw new BadRequestException("출석여부가 필요합니다.");
        }
    }
}
