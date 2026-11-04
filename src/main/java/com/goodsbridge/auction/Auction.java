package com.goodsbridge.auction;

import com.goodsbridge.item.Item;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

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
    private User currentWinnerUserId; // 현재 입찰자

    private Timestamp deadline; // 마감 시간

    private Integer depositAmount;  // 보증금

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Builder
    public Auction(Shop shop, Item item, Integer startPrice,
                   Timestamp deadline, Integer depositAmount) {
        this.shop = shop;
        this.item = item;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.currentWinnerUserId = null;
        this.deadline = deadline;
        this.depositAmount = depositAmount;
        this.status = Status.ACTIVE;
    }

    // 경매 입찰자
    public void updateCurrentBid(Integer bidAmount, User bidder) {
        this.currentPrice = bidAmount;
        this.currentWinnerUserId = bidder;
    }

    // 경매 마감 상태
    public void end() {
        this.status = currentWinnerUserId == null
            ? Status.ENDED_FAIL : Status.ENDED_SUCCESS;
    }
}
