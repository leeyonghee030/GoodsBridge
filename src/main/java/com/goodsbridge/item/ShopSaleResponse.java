package com.goodsbridge.item;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.purchase.DeliveryStatus;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseStatus;
import com.goodsbridge.refund.Refund;
import com.goodsbridge.refund.RequestedBy;
import com.goodsbridge.refund.Status;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// 가게 사장님 판매 상품 관리: 판매된 상품 1건 (구매자/배송/환불 정보 포함)
@Getter
public class ShopSaleResponse {
    private final Long purchaseId;
    private final Long itemId;
    private final Long shopId;
    private final String itemTitle;
    private final String categoryName;
    private final Integer price;
    private final String imageUrl;
    private final String buyerNickname;
    private final String deliveryAddress;
    private final DeliveryStatus deliveryStatus;
    private final String trackingNumber;
    private final PurchaseStatus purchaseStatus;
    private final String paymentId;
    private final Integer refundableAmount;
    private final LocalDateTime purchasedAt;
    private final List<RefundDto> refunds;

    public ShopSaleResponse(Purchase purchase, Payment payment, List<Refund> refunds) {
        Item item = purchase.getItem();
        this.purchaseId = purchase.getId();
        this.itemId = item.getId();
        this.shopId = item.getShopId();
        this.itemTitle = item.getTitle();
        this.categoryName = item.getCategory().getName();
        this.price = item.getPrice();
        this.imageUrl = item.getImageUrl();
        this.buyerNickname = purchase.getUser().getNickname();
        this.deliveryAddress = purchase.getDeliveryAddress();
        this.deliveryStatus = purchase.getDeliveryStatus();
        this.trackingNumber = purchase.getTrackingNumber();
        this.purchaseStatus = purchase.getStatus();
        this.paymentId = purchase.getPaymentId();
        this.refundableAmount = payment != null ? payment.getRefundableAmount() : 0;
        this.purchasedAt = purchase.getCreatedAt();
        this.refunds = refunds.stream().map(RefundDto::new).toList();
    }

    @Getter
    public static class RefundDto {
        private final Long id;
        private final Integer amount;
        private final String reason;
        private final String rejectReason;
        private final Status status;
        private final RequestedBy requestedBy;
        private final LocalDateTime requestedAt;

        RefundDto(Refund refund) {
            this.id = refund.getId();
            this.amount = refund.getRequestedAmount();
            this.reason = refund.getReason();
            this.rejectReason = refund.getRejectReason();
            this.status = refund.getStatus();
            this.requestedBy = refund.getRequestedBy();
            this.requestedAt = refund.getCreatedAt() != null ? refund.getCreatedAt().toLocalDateTime() : null;
        }
    }
}
