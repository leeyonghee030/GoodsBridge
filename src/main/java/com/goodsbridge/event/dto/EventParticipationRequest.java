package com.goodsbridge.event.dto;

import com.goodsbridge._core.exception.BadRequestException;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EventParticipationRequest {

    private String paymentId;
    // 구매 수량(번호표 장수). 이벤트별 1인당 최대 수량(최대 10개)까지, 안 보내면 1개
    private Integer quantity;

    public static final int MAX_QUANTITY = 10;

    public void validate() {
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("결제 정보가 확인되지 않습니다.");
        }
        if (quantity != null && (quantity < 1 || quantity > MAX_QUANTITY)) {
            throw new BadRequestException("수량은 1-" + MAX_QUANTITY + "개 사이로 선택해 주세요.");
        }
    }

    public int getQuantity() {
        return quantity == null ? 1 : quantity;
    }
}
