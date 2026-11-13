package com.goodsbridge.event.dto;

import com.goodsbridge.event.EventParticipation;
import com.goodsbridge.event.EventParticipationStatus;
import com.goodsbridge.event.ShopEventStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class EventParticipationResponse {

    private Long id;
    private Long shopEventId;
    private Long userId;
    private String username;
    private Integer ticketNumber;
    private Integer quantity;
    private EventParticipationStatus status;
    private LocalDateTime createdAt;
    // 마이페이지 이벤트 내역용: 이벤트 정보
    private String eventTitle;
    private String eventImageUrl;
    private String shopName;
    private Integer feeAmount;
    private LocalDateTime deadline;
    private LocalDate visitUntil;
    private ShopEventStatus eventStatus;

    @Builder
    public EventParticipationResponse(EventParticipation eventParticipation) {
        this.id = eventParticipation.getId();
        this.shopEventId = eventParticipation.getShopEvent().getId();
        this.userId = eventParticipation.getUser().getId();
        this.username = eventParticipation.getUser().getNickname();
        this.ticketNumber = eventParticipation.getTicketNumber();
        this.quantity = eventParticipation.getQuantity();
        this.status = eventParticipation.getStatus();
        this.createdAt = eventParticipation.getCreatedAt();
        this.eventTitle = eventParticipation.getShopEvent().getItem().getTitle();
        this.eventImageUrl = eventParticipation.getShopEvent().getItem().getImageUrl();
        this.shopName = eventParticipation.getShopEvent().getShop().getName();
        this.feeAmount = eventParticipation.getShopEvent().getFeeAmount();
        this.deadline = eventParticipation.getShopEvent().getDeadline();
        this.visitUntil = eventParticipation.getShopEvent().getVisitUntil();
        this.eventStatus = eventParticipation.getShopEvent().getStatus();
    }


}
