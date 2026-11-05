package com.goodsbridge.purchase;

import lombok.Data;
import com.goodsbridge._core.exception.BadRequestException;

public class PurchaseRequest {
    @Data
    public static class CreateDto {
        private String paymentId;
        private String deliveryAddress;

        public void validate() {
            if (paymentId == null || paymentId.isBlank()) {
                throw new BadRequestException("paymentId가 필요합니다.");
            }
            if (deliveryAddress == null || deliveryAddress.isBlank()) {
                throw new BadRequestException("배송지 주소가 필요합니다.");
            }
        }
    }

    @Data
    public static class ShippingDto {
        private String trackingNumber;
        private DeliveryStatus deliveryStatus;
    }
}
