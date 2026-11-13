package com.goodsbridge.exchange.dto;

import com.goodsbridge.exchange.ExchangeRequest;
import com.goodsbridge.exchange.ExchangeRequestStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 교환 제안/매칭 응답
@Getter
@NoArgsConstructor
public class ExchangeRequestResponse {

    private Long id;
    private Long exchangeItemAId;
    private Long itemBId;
    private String message;
    private Long shopId;
    private ExchangeRequestStatus status;
    private Integer depositAmountA;
    private Integer depositAmountB;
    private Boolean userAShowed;
    private Boolean userBShowed;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;

    // 마이페이지 교환 내역용 (내가 A인지 B인지, 상품명/사진, 만날 시간)
    private Long ownerUserId;
    private Long proposerUserId;
    private String itemTitle;
    private String itemImageUrl;
    private String itemBTitle;
    private String itemBImageUrl;
    private String shopName;
    private LocalDateTime meetingAt;
    private Long meetingProposerId;
    private Boolean meetingConfirmed;
    private LocalDateTime bothCheckedAt; // 가게가 둘 다 체크한 시각 (정산 유예 시작)
    private Integer acceptDepositAmount; // 게시글 주인(A)이 수락할 때 내야 하는 보증금 (서버 계산식과 동일)

    @Builder
    public ExchangeRequestResponse(Long id, Long exchangeItemAId, Long itemBId, String message, Long shopId,
                                    ExchangeRequestStatus status, Integer depositAmountA, Integer depositAmountB,
                                    Boolean userAShowed, Boolean userBShowed, LocalDateTime deadline, LocalDateTime createdAt,
                                    Long ownerUserId, Long proposerUserId, String itemTitle, String itemImageUrl,
                                    String itemBTitle, String itemBImageUrl, String shopName,
                                    LocalDateTime meetingAt, Long meetingProposerId, Boolean meetingConfirmed, Integer acceptDepositAmount,
                                    LocalDateTime bothCheckedAt) {
        this.id = id;
        this.exchangeItemAId = exchangeItemAId;
        this.itemBId = itemBId;
        this.message = message;
        this.shopId = shopId;
        this.status = status;
        this.depositAmountA = depositAmountA;
        this.depositAmountB = depositAmountB;
        this.userAShowed = userAShowed;
        this.userBShowed = userBShowed;
        this.deadline = deadline;
        this.createdAt = createdAt;
        this.ownerUserId = ownerUserId;
        this.proposerUserId = proposerUserId;
        this.itemTitle = itemTitle;
        this.itemImageUrl = itemImageUrl;
        this.itemBTitle = itemBTitle;
        this.itemBImageUrl = itemBImageUrl;
        this.shopName = shopName;
        this.meetingAt = meetingAt;
        this.meetingProposerId = meetingProposerId;
        this.meetingConfirmed = meetingConfirmed;
        this.acceptDepositAmount = acceptDepositAmount;
        this.bothCheckedAt = bothCheckedAt;
    }

    public static ExchangeRequestResponse from(ExchangeRequest exchangeRequest) {
        return ExchangeRequestResponse.builder()
            .id(exchangeRequest.getId())
            .exchangeItemAId(exchangeRequest.getExchangeItemA().getId())
            .itemBId(exchangeRequest.getItemB().getId())
            .message(exchangeRequest.getMessage())
            .shopId(exchangeRequest.getShop().getId())
            .status(exchangeRequest.getStatus())
            .depositAmountA(exchangeRequest.getDepositAmountA())
            .depositAmountB(exchangeRequest.getDepositAmountB())
            .userAShowed(exchangeRequest.getUserAShowed())
            .userBShowed(exchangeRequest.getUserBShowed())
            .deadline(exchangeRequest.getDeadline())
            .createdAt(exchangeRequest.getCreatedAt())
            .ownerUserId(exchangeRequest.getExchangeItemA().getUser().getId())
            .proposerUserId(exchangeRequest.getItemB().getSellerUserId())
            .itemTitle(exchangeRequest.getExchangeItemA().getItem().getTitle())
            .itemImageUrl(exchangeRequest.getExchangeItemA().getItem().getImageUrl())
            .itemBTitle(exchangeRequest.getItemB().getTitle())
            .itemBImageUrl(exchangeRequest.getItemB().getImageUrl())
            .shopName(exchangeRequest.getShop().getName())
            .meetingAt(exchangeRequest.getMeetingAt())
            .meetingProposerId(exchangeRequest.getMeetingProposerId())
            .meetingConfirmed(exchangeRequest.isMeetingConfirmed())
            .acceptDepositAmount(Math.max(Math.round(exchangeRequest.getExchangeItemA().getItem().getPrice() * 0.2f), 2000))
            .bothCheckedAt(exchangeRequest.getBothCheckedAt())
            .build();
    }
}
