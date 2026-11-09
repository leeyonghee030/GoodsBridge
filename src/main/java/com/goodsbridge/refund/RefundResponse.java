package com.goodsbridge.refund;

import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

public class RefundResponse {
    @Data
    public static class ListDto {
        private Long id;
        private Integer requestedAmount;
        private String paymentId;
        private String reason;
        private String rejectReason;
        private Timestamp requestedAt;
        private Status status;
        private boolean approved;
        private boolean requested;
        private boolean rejected;

        public ListDto(Refund refund) {
            this.id = refund.getId();
            this.requestedAmount = refund.getRequestedAmount();
            this.paymentId = refund.getPayment().getPaymentId();
            this.reason = refund.getReason();
            this.rejectReason = refund.getRejectReason();
            this.requestedAt = refund.getCreatedAt();
            this.status = refund.getStatus();
            this.approved = refund.getStatus() == Status.APPROVED;
            this.requested = refund.getStatus() == Status.REQUESTED;
            this.rejected = refund.getStatus() == Status.REJECTED;
        }
    }
}
