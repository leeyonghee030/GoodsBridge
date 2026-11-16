package com.goodsbridge.admin;

import com.goodsbridge.payment.PaymentStatus;
import lombok.Getter;

import java.time.LocalDateTime;

// 관리자 구독 결제 내역의 한 줄. 쿼리 결과를 그대로 받는 객체라서 생성자 인자 순서가 쿼리와 같아야 한다.
@Getter
public class AdminSubscriptionPaymentItem {

    private final String paymentId;
    private final Long payerUserId;
    private final String payerNickname;
    private String shopName;            // 결제와 연결된 구독의 가게. 연결이 없으면 서비스가 결제자의 가게로 채운다.
    private final Integer amount;
    private final Integer refundedAmount;
    private final PaymentStatus status;
    private final LocalDateTime paidAt;

    public AdminSubscriptionPaymentItem(String paymentId, Long payerUserId, String payerNickname, String shopName,
                                        Integer amount, Integer refundedAmount, PaymentStatus status,
                                        LocalDateTime paidAt) {
        this.paymentId = paymentId;
        this.payerUserId = payerUserId;
        this.payerNickname = payerNickname;
        this.shopName = shopName;
        this.amount = amount == null ? 0 : amount;
        this.refundedAmount = refundedAmount == null ? 0 : refundedAmount;
        this.status = status;
        this.paidAt = paidAt;
    }

    void fillShopName(String shopName) {
        this.shopName = shopName;
    }
}
