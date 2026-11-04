package com.goodsbridge.payment;

import lombok.Builder;
import lombok.Data;

public class PaymentResponse {
    // PortOne V2 결제 단건 조회 응답
    @Data
    public static class PortOnePayment {
        private String id;
        private String status;
        private String pgTxId;
        private Amount amount;

        @Data
        public static class Amount {
            private Integer total;
        }
    }

    @Data
    public static class DetailDto {
        private Long id;
        private String paymentId;
        private int amount;
        private int refundedAmount;
        private PaymentStatus paymentStatus;
        private PaymentPurpose paymentPurpose;

        @Builder
        public DetailDto(Payment payment) {
            this.id = payment.getId();
            this.paymentId = payment.getPaymentId();
            this.amount = payment.getAmount();
            this.refundedAmount = payment.getRefundedAmount();
            this.paymentStatus = payment.getStatus();
            this.paymentPurpose = payment.getPurpose();
        }
    }
}
