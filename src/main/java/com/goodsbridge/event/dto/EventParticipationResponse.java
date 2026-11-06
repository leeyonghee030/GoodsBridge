package com.goodsbridge.event.dto;

import com.goodsbridge.event.EventParticipation;
import com.goodsbridge.event.EventParticipationStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class EventParticipationResponse {

    private Long id;
    private Long shopEventId;
    private Long userId;
    private String username;
    private Integer ticketNumber;
    private EventParticipationStatus status;
    private LocalDateTime createdAt;

    @Builder
    public EventParticipationResponse(EventParticipation eventParticipation) {
        this.id = eventParticipation.getId();
        this.shopEventId = eventParticipation.getShopEvent().getId();
        this.userId = eventParticipation.getUser().getId();
        this.username = eventParticipation.getUser().getNickname();
        this.ticketNumber = eventParticipation.getTicketNumber();
        this.status = eventParticipation.getStatus();
        this.createdAt = eventParticipation.getCreatedAt();
    }


}
