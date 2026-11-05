package com.goodsbridge.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;

public class PaymentResponse {
    // 결제 창 띄우기
    @Data
    public static class PrepareDto{
        private String paymentId;
        private int amount;
        private String storeId;
        private String channelKey;

        @Builder
        public PrepareDto(String paymentId, int amount, String storeId, String channelKey) {
            this.paymentId = paymentId;
            this.amount = amount;
            this.storeId = storeId;
            this.channelKey = channelKey;
        }
    }

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

    // 포트원 결제 취소 응답
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PortOneCancel {
        Cancellation cancellation;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Cancellation {
            private String status;
            private String id;
            private int totalAmount;
        }
    }
}
