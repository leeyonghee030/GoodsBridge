package com.goodsbridge.purchase;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.Item;
import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "purchase_tb")
@NoArgsConstructor
public class Purchase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;    // PK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;  // 상품 FK

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_user_id", nullable = false)
    private User user;  // 사용자 FK

    @Column(nullable = false)
    private String paymentId;   // 결제 FK (주의사항에 따라 FK 설계를 하지 않음)

    @Column(nullable = false)
    private String deliveryAddress; // 배송 주소

    private String trackingNumber;  // 운송번호

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryStatus deliveryStatus;  // 배송 상태

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status;  // 구매 상태

    @Builder
    public Purchase(Item item, User user, String paymentId, String deliveryAddress) {
        this.item = item;
        this.user = user;
        this.paymentId = paymentId;
        this.deliveryAddress = deliveryAddress;
        this.deliveryStatus = DeliveryStatus.PREPARING;
        this.status = PurchaseStatus.PAID;
    }

    // 배송
    public void ship(String trackingNumber) {
        if (status != PurchaseStatus.PAID) {
            throw new BadRequestException("결제 완료 상태에서만 배송을 시작할 수 있습니다.");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new BadRequestException("운송장 번호가 필요합니다.");
        }
        this.trackingNumber = trackingNumber;
        this.deliveryStatus = DeliveryStatus.SHIPPED;
    }

    // 배송 상태
    public void markDelivered() {
        if (deliveryStatus != DeliveryStatus.SHIPPED) throw new BadRequestException("배송중 상태에서만 배송완료로 변경할 수 있습니다.");
        this.deliveryStatus = DeliveryStatus.DELIVERED;
    }

    // 수령확인
    public void confirm() {
        if (status != PurchaseStatus.PAID || deliveryStatus == DeliveryStatus.PREPARING) {
            throw new BadRequestException("배송이 완료되기 전에는 수령 확인을 할 수 없습니다.");
        }
        this.status = PurchaseStatus.CONFIRMED;
    }
}
