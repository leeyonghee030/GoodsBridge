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

    // 번호표 (1인 1장. 추가 구매 건도 같은 번호표를 쓰고, 구매 수량은 건마다 quantity 로 따로)
    @Column(nullable = false)
    private Integer ticketNumber;

    // 이 구매 건의 수량 (한 사람의 합계가 1인당 최대 10개). 참가비/환불/정산 금액 = 참가비 × 수량. 추가 구매는 별도 행(별도 결제)
    @Column(nullable = false, columnDefinition = "int default 1")
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventParticipationStatus status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public EventParticipation(ShopEvent shopEvent, User user, String paymentId, Integer ticketNumber, Integer quantity) {
        this.shopEvent = shopEvent;
        this.user = user;
        this.paymentId = paymentId;
        this.ticketNumber = ticketNumber;
        this.quantity = quantity == null ? 1 : quantity;
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

    // 유저가 이 구매 건을 취소 (번호표는 같은 번호표의 다른 구매 건이 남아 있으면 유지된다)
    public void cancel() {
        this.status = EventParticipationStatus.CANCELLED;
    }

    // 가게가 이벤트를 취소해 환불된 참가
    public void refund() {
        this.status = EventParticipationStatus.REFUNDED;
    }
}
