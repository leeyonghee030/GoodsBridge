package com.goodsbridge.auction;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.Item;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "auction_tb")
@NoArgsConstructor
public class Auction {
    // 낙찰 후 잔금 결제 기한(일) - 기한 내 미결제 시 보증금은 가게가 가져간다
    // 테스트에서는 분으로 테스트
    public static final int BALANCE_DUE_MINUTES = 3;

    // 안티 스나이핑 용
    public static final int EXTENSION_THRESHOLD_MINUTES = 2;
    public static final int EXTENSION_MINUTES = 5;
    public static final int MAX_EXTENSION_COUNT = 3;

    // 안티 스나이핑 용
    @Column(name = "extension_count", nullable = false)
    private int extensionCount = 0;

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;  // 상점

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private Item item;  // 물품

    private Integer startPrice;     // 시작가
    private Integer currentPrice;   // 현재 금액

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_winner_user_id")
    private User currentWinner; // 현재 입찰자

    @Column(name = "winning_payment_id")
    @Setter
    private String winningPaymentId;  // 낙찰 결제id 저장

    private LocalDateTime deadline; // 마감 시간

    private Integer depositAmount;  // 보증금

    @Column(name = "balance_payment_id")
    private String balancePaymentId;    // 낙찰자 잔금(낙찰가 - 보증금) 결제 id, null이면 미결제

    @Column(name = "balance_due_at")
    private LocalDateTime balanceDueAt; // 잔금 결제 기한 (마감 시점 + 분)

    @Column(name = "losers_refunded", nullable = false, columnDefinition = "boolean default false")
    private boolean losersRefunded = false; // 패찰자 보증금 환불 완료 여부

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;  // 경매 상태(낙찰, 취소, 실패, 진행 등)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;    // DB 기록용 시간

    @Builder
    public Auction(Shop shop, Item item, Integer startPrice,
                   LocalDateTime deadline, Integer depositAmount) {
        this.shop = shop;
        this.item = item;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.currentWinner = null;
        this.deadline = deadline;
        this.depositAmount = depositAmount;
        this.status = Status.ACTIVE;
    }

    // 경매 입찰자
    public void updateCurrentBid(Integer bidAmount, User bidder) {
        this.currentPrice = bidAmount;
        this.currentWinner = bidder;
    }

    // 경매 마감 상태 (낙찰이면 잔금 결제 기한도 함께 정한다)
    public void end() {
        this.status = currentWinner == null
            ? Status.ENDED_FAIL : Status.ENDED_SUCCESS;
        if (this.status == Status.ENDED_SUCCESS) {
            this.balanceDueAt = LocalDateTime.now().plusMinutes(BALANCE_DUE_MINUTES);
        }
    }

    // 경매 취소 (진행 중인 경매만). 입찰자 보증금 환불은 스케줄러가 처리한다.
    public void cancel() {
        if (status != Status.ACTIVE) {
            throw new BadRequestException("진행 중인 경매만 취소할 수 있습니다.");
        }
        this.status = Status.CANCELLED;
    }

    // 안티 스나이핑용
    public boolean extendIfNearDeadline(LocalDateTime now) {
        if (now.isBefore(deadline.minusMinutes(EXTENSION_THRESHOLD_MINUTES))) {
            return false;
        }

        if (extensionCount >= MAX_EXTENSION_COUNT) {
            return false;
        }

        deadline = deadline.plusMinutes(EXTENSION_MINUTES);
        extensionCount++;
        return true;
    }

    // 잔금 = 낙찰가 - 이미 낸 보증금
    public int getBalanceAmount() {
        return Math.max(0, currentPrice - depositAmount);
    }

    public boolean isBalancePaid() {
        return balancePaymentId != null;
    }

    // 기한이 정해져 있고(마감 처리된 경매) 그 시각을 지났는지
    public boolean isBalanceOverdue(LocalDateTime now) {
        return balanceDueAt != null && now.isAfter(balanceDueAt);
    }

    public void completeBalance(String paymentId) {
        this.balancePaymentId = paymentId;
    }

    // 잔금 미납 -> 낙찰 무효(보증금은 가게 몫으로 정산)
    public void forfeitUnpaid() {
        this.status = Status.ENDED_FAIL;
    }

    public void markLosersRefunded() {
        this.losersRefunded = true;
    }
}
