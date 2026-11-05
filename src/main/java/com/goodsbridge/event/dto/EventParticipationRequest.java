package com.goodsbridge.event.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventParticipationRequest {

    private String paymentId;

    public void validate() {
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("결제 정보가 확인되지 않습니다.");
        }
    }
}
