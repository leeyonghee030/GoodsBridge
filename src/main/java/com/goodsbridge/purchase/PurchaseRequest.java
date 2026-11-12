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
            if (paymentId.length() > 100) {
                throw new BadRequestException("paymentId가 너무 깁니다.");
            }
            if (deliveryAddress == null || deliveryAddress.isBlank()) {
                throw new BadRequestException("배송지 주소가 필요합니다.");
            }
            if (deliveryAddress.length() > 255) {
                throw new BadRequestException("배송지 주소는 255자 이하로 입력해주세요.");
            }
        }
    }

    @Data
    public static class ShippingDto {
        private String trackingNumber;
        private DeliveryStatus deliveryStatus;
    }
}
