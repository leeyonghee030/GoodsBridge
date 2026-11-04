package com.goodsbridge.purchase;

import lombok.Data;

public class PurchaseResponse {
    @Data
    public static class DetailDto {
        private Long id;    // 구매 내역 PK
        private Long itemId;
        private String paymentId;
        private String deliveryAddress;
        private String trackingNumber;
        private DeliveryStatus deliveryStatus;
        private PurchaseStatus status;

        public DetailDto(Purchase purchase) {
            this.id = purchase.getId();
            this.itemId = purchase.getItem().getId();
            this.paymentId = purchase.getPaymentId();
            this.deliveryAddress = purchase.getDeliveryAddress();
            this.trackingNumber = purchase.getTrackingNumber();
            this.deliveryStatus = purchase.getDeliveryStatus();
            this.status = purchase.getStatus();
        }
    }
}
