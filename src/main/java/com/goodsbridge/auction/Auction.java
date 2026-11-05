package com.goodsbridge.auction;

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

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

    // 경매 마감 상태
    public void end() {
        this.status = currentWinner == null
            ? Status.ENDED_FAIL : Status.ENDED_SUCCESS;
    }

    public void updateCurrentPrice(Integer bidAmount) {
        this.currentPrice = bidAmount;
    }
}
