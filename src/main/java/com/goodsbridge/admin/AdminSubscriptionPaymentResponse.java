package com.goodsbridge.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminSubscriptionPaymentResponse {

    private String month;               // YYYY-MM
    private long paymentCount;          // 결제 건수
    private long totalAmount;           // 결제 금액 합계
    private long refundedAmount;        // 환불 금액 합계
    private long netAmount;             // 플랫폼 매출 = 결제 - 환불
    private List<AdminSubscriptionPaymentItem> payments;    // 최근 결제가 먼저
}
