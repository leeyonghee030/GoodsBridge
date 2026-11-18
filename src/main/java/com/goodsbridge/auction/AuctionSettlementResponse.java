package com.goodsbridge.auction;

import lombok.Getter;

import java.time.LocalDateTime;

// 가게 경매 정산 내역 1건 (낙찰 정산 / 낙찰자 미결제로 받은 보증금)
@Getter
public class AuctionSettlementResponse {
    private final Long auctionId;
    private final String itemTitle;
    private final String type;          // AUCTION_SALE / NO_SHOW_FEE
    private final Integer amount;       // 가게가 받은 금액
    private final Integer depositAmount;
    private final Integer balanceAmount;
    private final LocalDateTime createdAt;

    public AuctionSettlementResponse(Auction auction, String type, Integer amount, LocalDateTime createdAt) {
        this.auctionId = auction.getId();
        this.itemTitle = auction.getItem().getTitle();
        this.type = type;
        this.amount = amount;
        this.depositAmount = auction.getDepositAmount();
        this.balanceAmount = auction.getBalanceAmount();
        this.createdAt = createdAt;
    }
}
