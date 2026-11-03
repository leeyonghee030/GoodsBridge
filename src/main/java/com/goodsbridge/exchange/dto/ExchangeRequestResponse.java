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

    @Builder
    public ExchangeRequestResponse(Long id, Long exchangeItemAId, Long itemBId, String message, Long shopId,
                                    ExchangeRequestStatus status, Integer depositAmountA, Integer depositAmountB,
                                    Boolean userAShowed, Boolean userBShowed, LocalDateTime deadline, LocalDateTime createdAt) {
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
            .build();
    }
}
