package com.goodsbridge.event.dto;


import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ShopEventRequest {
    private Long itemId;
    private Integer capacity;
    private Integer feeAmount;
    private LocalDateTime deadline;

    public void validate() {
        if (itemId == null) {
            throw new BadRequestException("아이템을 지정해 주세요.");
        }
        if (capacity == null || capacity <= 0 || capacity > 100) {
            throw new BadRequestException("모집 인원은 1-100명 사이 값이여야 합니다.");
        }
        if (feeAmount == null || feeAmount < 5000 || feeAmount > 100000) {
            throw new BadRequestException("참가비는 5,000 - 100,000 사이 값이여야  합니다.");
        }
        if (deadline == null || deadline.isBefore(LocalDateTime.now())) {
            throw new BadRequestException("마감 시각은 현재보다 미래여야 합니다.");
        }
    }
}
