package com.goodsbridge.exchange;

import com.goodsbridge.item.Item;
import com.goodsbridge.shop.Shop;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "exchangeRequest_tb")
@NoArgsConstructor
public class ExchangeRequest {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchangeItemAId", nullable = false)
    private ExchangeItem exchangeItemA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itemBId", nullable = false)
    private Item itemB;

    private String message; // B가 A에게 보내는 짧은 제안 메시지

    @Column(nullable = false)
    private String paymentIdB; // B 보증금 결제 식별자 (환불 시 사용)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shopId", nullable = false)
    private Shop shop;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExchangeRequestStatus status;

    //보증금 금액
    private Integer depositAmountA;
    @Column(nullable = false)
    private Integer depositAmountB;

    //교환 당일 가게에 실제로 나타났는지 체크
    @Column(nullable = false)
    private Boolean userAShowed;
    @Column(nullable = false)
    private Boolean userBShowed;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public ExchangeRequest(ExchangeItem exchangeItemA, Item itemB, String message, String paymentIdB, Shop shop, Integer depositAmountB) {

        this.exchangeItemA = exchangeItemA;
        this.itemB = itemB;
        this.message = message;
        this.paymentIdB = paymentIdB;
        this.shop = shop;
        this.status = ExchangeRequestStatus.PENDING;
        this.depositAmountB = depositAmountB < 2000 ? 2000 : depositAmountB;
        this.deadline = LocalDateTime.now().plusDays(3);
    }

    //편의메서드

    // 게시글 주인(A)인지 확인
    public boolean isOwner(Long userId) {
        return this.exchangeItemA.getUser().getId().equals(userId);
    }

    // 제안자(B)인지 확인
    public boolean isProposer(Long userId) {
        return this.itemB.getSellerUserId().equals(userId);
    }

    // 교환 수락시 depositAmountA 보증금 업데이트
    public void updateDepositAmountA(Integer depositAmountA) {
        this.depositAmountA = depositAmountA < 2000 ? 2000 : depositAmountA;
    }

    // 수락(accept) 시 deadline을 "가게 방문 기한"(+5일)으로 덮어씀
    public void updateDeadlineForVisit() {
        this.deadline = LocalDateTime.now().plusDays(5);
    }

    // 가게 체크인 시 출석여부 기록
    public void checkIn(boolean userAShowed, boolean userBShowed) {
        this.userAShowed = userAShowed;
        this.userBShowed = userBShowed;
    }

    // 상태 변경
    public void updateStatus(ExchangeRequestStatus status) {
        this.status = status;
    }

    // 데드라인 지났는지
    public boolean isDeadlinePassed() {
        return LocalDateTime.now().isAfter(this.deadline);
    }

    // 상태가 대기중인지?
    public boolean isPending() {
        return this.status == ExchangeRequestStatus.PENDING;
    }

    // 상태가 확정됨인지?
    public boolean isConfirmed() {
        return this.status == ExchangeRequestStatus.CONFIRMED;
    }

    // 상태가 끝났는지?
    public boolean isDone() {
        return this.status == ExchangeRequestStatus.COMPLETED
            || this.status == ExchangeRequestStatus.EXPIRED_PARTIAL
            || this.status == ExchangeRequestStatus.EXPIRED_BOTH;
    }

}
