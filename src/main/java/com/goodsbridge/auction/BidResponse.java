package com.goodsbridge.auction;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class BidResponse {
    private Long bidId;
    private Long auctionId;
    private String nickname;
    private Long userId;
    private Integer bidAmount;
    private LocalDateTime createdAt;

    public static BidResponse from(Bid bid) {
        return BidResponse.builder()
            .bidId(bid.getId())
            .auctionId(bid.getAuction().getId())
            .nickname(bid.getUser().getNickname())
            .userId(bid.getUser().getId())
            .bidAmount(bid.getBidAmount())
            .createdAt(bid.getCreatedAt())
            .build();
    }
}
