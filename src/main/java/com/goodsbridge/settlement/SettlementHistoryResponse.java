package com.goodsbridge.settlement;

import lombok.Data;

import java.time.LocalDateTime;

public class SettlementHistoryResponse {
    @Data
    public static class ListDto {
        private Long id;
        private Long shopId;
        private String paymentId;
        private Integer amount;
        private SettlementHistoryType type;
        private LocalDateTime createdAt;

        public ListDto(SettlementHistory settlementHistory) {
            this.id = settlementHistory.getId();
            this.shopId = settlementHistory.getShop().getId();
            this.paymentId = settlementHistory.getPayment().getPaymentId();
            this.amount = settlementHistory.getAmount();
            this.type = settlementHistory.getType();
            this.createdAt = settlementHistory.getCreatedAt();
        }
    }
}
