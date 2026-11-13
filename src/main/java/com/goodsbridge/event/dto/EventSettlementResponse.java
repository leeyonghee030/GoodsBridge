package com.goodsbridge.event.dto;

import com.goodsbridge.event.ShopEvent;
import com.goodsbridge.event.ShopEventStatus;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 가게 이벤트 정산 보기: 이벤트별 참가비 합계 / 취소·환불 / 정산 상태
@Getter
public class EventSettlementResponse {

    private final Long eventId;
    private final String itemTitle;
    private final String itemImageUrl;
    private final ShopEventStatus status;
    private final LocalDateTime deadline;
    private final LocalDate visitUntil;
    private final Integer feeAmount;
    private final boolean settled;            // 마감 후 정산 기록까지 끝났는지
    private final int joinedQuantity;         // 참가중인 수량 (정산 대상)
    private final int joinedAmount;           // 참가중 참가비 합계 (= 정산 금액, 플랫폼 수수료 없음)
    private final int cancelledQuantity;      // 손님이 취소한 수량
    private final int cancelledAmount;        // 손님 취소로 환불된 금액
    private final int refundedQuantity;       // 가게가 이벤트를 취소해 환불된 수량
    private final int refundedAmount;

    public EventSettlementResponse(ShopEvent event, int joinedQuantity, int cancelledQuantity, int refundedQuantity) {
        this.eventId = event.getId();
        this.itemTitle = event.getItem().getTitle();
        this.itemImageUrl = event.getItem().getImageUrl();
        this.status = event.getStatus();
        this.deadline = event.getDeadline();
        this.visitUntil = event.getVisitUntil();
        this.feeAmount = event.getFeeAmount();
        this.settled = event.isSettled();
        this.joinedQuantity = joinedQuantity;
        this.joinedAmount = joinedQuantity * event.getFeeAmount();
        this.cancelledQuantity = cancelledQuantity;
        this.cancelledAmount = cancelledQuantity * event.getFeeAmount();
        this.refundedQuantity = refundedQuantity;
        this.refundedAmount = refundedQuantity * event.getFeeAmount();
    }
}
