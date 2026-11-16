package com.goodsbridge.event.dto;

import lombok.Getter;

import java.time.LocalDateTime;

// 가게 이벤트 정산 내역 한 줄: 정산(SettlementHistory, 이벤트 참가비)된 구매 건
@Getter
public class EventSettlementRecordResponse {

    private final LocalDateTime settledAt;   // 정산된 시각 (달력에서 날짜별로 묶는 기준)
    private final int amount;                // 정산 금액
    private final Long eventId;
    private final String itemTitle;
    private final String buyerNickname;
    private final int quantity;

    public EventSettlementRecordResponse(LocalDateTime settledAt, Integer amount, Long eventId, String itemTitle,
                                         String buyerNickname, Integer quantity) {
        this.settledAt = settledAt;
        this.amount = amount == null ? 0 : amount;
        this.eventId = eventId;
        this.itemTitle = itemTitle;
        this.buyerNickname = buyerNickname;
        this.quantity = quantity == null ? 0 : quantity;
    }
}
