package com.goodsbridge.event;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "event_participation_tb")
@NoArgsConstructor
public class EventParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopEventId", nullable = false)
    private ShopEvent shopEvent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    @Column(nullable = false)
    private String paymentId;

    @Column(nullable = false)
    private Integer ticketNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventParticipationStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public EventParticipation(ShopEvent shopEvent, User user, String paymentId, Integer ticketNumber) {
        this.shopEvent = shopEvent;
        this.user = user;
        this.paymentId = paymentId;
        this.ticketNumber = ticketNumber;
        this.status = EventParticipationStatus.JOINED;
    }

    // 편의메서드

    //이벤트 참여자 본인인지
    public boolean isOwner(Long userId){
        return this.user.getId().equals(userId);
    }

    //이밴트 참여중인지?
    public boolean isJoined() {
        return this.status == EventParticipationStatus.JOINED;
    }

    // 가게가 이벤트를 취소해 환불된 참가
    public void refund() {
        this.status = EventParticipationStatus.REFUNDED;
    }
}
