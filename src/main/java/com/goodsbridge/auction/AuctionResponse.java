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
    private boolean balancePaid;            // 낙찰 잔금 결제 완료 여부
    private Integer balanceAmount;          // 낙찰 성공 시 낙찰자가 낼 잔금 (낙찰가 - 보증금)
    private LocalDateTime balanceDueAt;     // 잔금 결제 기한

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
        this.balancePaid = auction.isBalancePaid();
        this.balanceAmount = auction.getStatus() == Status.ENDED_SUCCESS ? auction.getBalanceAmount() : null;
        this.balanceDueAt = auction.getBalanceDueAt();
    }

    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(auction);
    }
}
