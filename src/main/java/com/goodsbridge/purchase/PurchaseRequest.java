package com.goodsbridge.purchase;

import lombok.Data;

public class PurchaseRequest {
    @Data
    public static class CreateDto {
        private String paymentId;
        private String deliveryAddress;
    }

    @Data
    public static class ShippingDto {
        private String trackingNumber;
        private DeliveryStatus deliveryStatus;
    }
}
