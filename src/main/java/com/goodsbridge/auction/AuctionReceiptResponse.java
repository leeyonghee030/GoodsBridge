package com.goodsbridge.auction;

import com.goodsbridge.purchase.DeliveryStatus;
import com.goodsbridge.purchase.Purchase;
import lombok.Getter;

import java.time.LocalDateTime;

// 가게 사장님용 경매 영수증: 낙찰가, 낙찰자, 결제/배송 정보 (잔금 결제 전이면 낙찰자·기한만)
@Getter
public class AuctionReceiptResponse {
    private final Long auctionId;
    private final String itemTitle;
    private final String itemImageUrl;
    private final String shopName;
    private final Status status;
    private final String result;               // WAITING(잔금 대기) / DONE(정산 완료) / FORFEITED(미결제 · 보증금 몰수) / FAIL(유찰)
    private final Integer startPrice;
    private final Integer finalPrice;           // 낙찰가
    private final Integer depositAmount;
    private final Integer balanceAmount;
    private final long bidCount;
    private final LocalDateTime deadline;
    private final String winnerNickname;
    private final LocalDateTime balanceDueAt;
    private final LocalDateTime paidAt;         // 잔금 결제 시각
    private final String deliveryAddress;
    private final DeliveryStatus deliveryStatus;
    private final String trackingNumber;

    public AuctionReceiptResponse(Auction auction, long bidCount, Purchase purchase) {
        this.auctionId = auction.getId();
        this.itemTitle = auction.getItem().getTitle();
        this.itemImageUrl = auction.getItem().getImageUrl();
        this.shopName = auction.getShop().getName();
        this.status = auction.getStatus();
        this.startPrice = auction.getStartPrice();
        this.finalPrice = auction.getCurrentPrice();
        this.depositAmount = auction.getDepositAmount();
        this.bidCount = bidCount;
        this.deadline = auction.getDeadline();
        boolean hasWinner = auction.getCurrentWinner() != null;
        this.winnerNickname = hasWinner ? auction.getCurrentWinner().getNickname() : null;
        this.balanceAmount = hasWinner ? auction.getBalanceAmount() : null;
        this.balanceDueAt = auction.getBalanceDueAt();
        this.result = auction.getStatus() == Status.ENDED_SUCCESS
            ? (auction.isBalancePaid() ? "DONE" : "WAITING")
            : auction.getStatus() == Status.ENDED_FAIL ? (hasWinner ? "FORFEITED" : "FAIL") : null;
        this.paidAt = purchase != null ? purchase.getCreatedAt() : null;
        this.deliveryAddress = purchase != null ? purchase.getDeliveryAddress() : null;
        this.deliveryStatus = purchase != null ? purchase.getDeliveryStatus() : null;
        this.trackingNumber = purchase != null ? purchase.getTrackingNumber() : null;
    }
}
