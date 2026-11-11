package com.goodsbridge.exchange.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 만날 시간 제안 (POST /api/exchange-requests/{id}/meeting)
@Getter
@NoArgsConstructor
public class ExchangeMeetingRequest {

    private LocalDateTime meetingAt;

    public void validate() {
        if (meetingAt == null) {
            throw new BadRequestException("만날 시간을 선택해주세요.");
        }
    }
}
