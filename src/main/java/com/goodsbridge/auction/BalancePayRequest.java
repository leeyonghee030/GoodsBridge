package com.goodsbridge.auction;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class BalancePayRequest {
    private String paymentId;   // PortOne 결제 id (금액은 서버가 계산하므로 받지 않는다)
}
