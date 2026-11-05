package com.goodsbridge.auction;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class AuctionResponse {
    // 경매 목록
    private Long id;
    private Long shopId;
    private Long itemId;
    private Integer startPrice;
    private Integer currentPrice;
    private Long currentWinnerUserId;
    private LocalDateTime deadline;
    private Integer depositAmount;
    private Status status;

    public AuctionResponse(Auction auction) {
        this.id = auction.getId();
        this.shopId = auction.getShop().getId();
        this.itemId = auction.getItem().getId();
        this.startPrice = auction.getStartPrice();
        this.currentPrice = auction.getCurrentPrice();
        this.currentWinnerUserId = auction.getCurrentWinner() != null
            ? auction.getCurrentWinner().getId() : null;
        this.deadline = auction.getDeadline();
        this.depositAmount = auction.getDepositAmount();
        this.status = auction.getStatus();
    }

    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(auction);
    }
}
