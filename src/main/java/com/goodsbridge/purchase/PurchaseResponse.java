package com.goodsbridge.purchase;

import lombok.Data;

public class PurchaseResponse {
    @Data
    public static class DetailDto {
        private Long id;    // 구매 내역 PK
        private Long itemId;
        private String itemTitle;       // 상품명
        private PurchaseSource source;  // 구매 경로 (상점/경매)
        private Integer amount;         // 결제(낙찰) 금액
        private String paymentId;
        private String deliveryAddress;
        private String trackingNumber;
        private DeliveryStatus deliveryStatus;
        private PurchaseStatus status;

        public DetailDto(Purchase purchase) {
            this.id = purchase.getId();
            this.itemId = purchase.getItem().getId();
            this.itemTitle = purchase.getItem().getTitle();
            this.source = purchase.getSource();
            this.amount = purchase.getAmount();
            this.paymentId = purchase.getPaymentId();
            this.deliveryAddress = purchase.getDeliveryAddress();
            this.trackingNumber = purchase.getTrackingNumber();
            this.deliveryStatus = purchase.getDeliveryStatus();
            this.status = purchase.getStatus();
        }
    }
}
