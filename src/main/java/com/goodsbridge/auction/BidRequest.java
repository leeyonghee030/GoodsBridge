package com.goodsbridge.auction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BidRequest {
    private Integer bidAmount;
    private String paymentId;
}
