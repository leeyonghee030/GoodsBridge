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

    // 화면용: 경매 상품/가게 정보와 입찰 횟수
    private String itemTitle;
    private String itemImageUrl;
    private String itemDescription;
    private String categoryName;
    private String shopName;
    private String shopAddress;
    private long bidCount;                  // 입찰 횟수 (목록/상세에서 채운다, 그 외에는 0)

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
        this.itemTitle = auction.getItem().getTitle();
        this.itemImageUrl = auction.getItem().getImageUrl();
        this.itemDescription = auction.getItem().getDescription();
        this.categoryName = auction.getItem().getCategory() != null ? auction.getItem().getCategory().getName() : null;
        this.shopName = auction.getShop().getName();
        this.shopAddress = auction.getShop().getAddress();
    }

    public AuctionResponse withBidCount(long bidCount) {
        this.bidCount = bidCount;
        return this;
    }

    public static AuctionResponse from(Auction auction) {
        return new AuctionResponse(auction);
    }
}
