package com.goodsbridge.purchase;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.Item;
import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Table(name = "purchase_tb", uniqueConstraints = @UniqueConstraint(name = "uk_purchase_item", columnNames = "item_id"))
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

    // 구매 경로 (기존 행은 DB 기본값 SHOP)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, columnDefinition = "varchar(20) default 'SHOP'")
    private PurchaseSource source = PurchaseSource.SHOP;

    // 실제 결제(낙찰) 금액. 경매는 낙찰가 전체(보증금 + 잔금)
    private Integer amount;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public Purchase(Item item, User user, String paymentId, String deliveryAddress,
                    PurchaseSource source, Integer amount) {
        this.item = item;
        this.user = user;
        this.paymentId = paymentId;
        this.deliveryAddress = deliveryAddress;
        this.source = source == null ? PurchaseSource.SHOP : source;
        this.amount = amount;
        this.deliveryStatus = DeliveryStatus.PREPARING;
        this.status = PurchaseStatus.PAID;
    }

    public boolean isAuction() {
        return source == PurchaseSource.AUCTION;
    }

    // 배송
    public void ship(String trackingNumber) {
        if (status != PurchaseStatus.PAID) {
            throw new BadRequestException("결제 완료 상태에서만 배송을 시작할 수 있습니다.");
        }
        // 배송 완료 후에는 상태를 SHIPPED 로 되돌리거나 운송장을 바꿀 수 없다.
        if (deliveryStatus == DeliveryStatus.DELIVERED) {
            throw new BadRequestException("배송이 완료된 상품은 운송장 번호를 수정할 수 없습니다.");
        }
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new BadRequestException("운송장 번호가 필요합니다.");
        }
        String trimmed = trackingNumber.trim();
        if (trimmed.length() > 50) {
            throw new BadRequestException("운송장 번호는 50자 이하로 입력해주세요.");
        }
        this.trackingNumber = trimmed;
        this.deliveryStatus = DeliveryStatus.SHIPPED;
    }

    // 배송 상태
    public void markDelivered() {
        if (deliveryStatus != DeliveryStatus.SHIPPED) throw new BadRequestException("배송중 상태에서만 배송완료로 변경할 수 있습니다.");
        this.deliveryStatus = DeliveryStatus.DELIVERED;
    }

    // 수령확인
    public void confirm() {
        // 판매자가 배송 완료 처리한 뒤에만 수령 확인할 수 있다. (먼저 확인하면 판매자가 배송 완료 처리를 못 하게 됨)
        if (status != PurchaseStatus.PAID || deliveryStatus != DeliveryStatus.DELIVERED) {
            throw new BadRequestException("배송이 완료된 후에만 수령 확인을 할 수 있습니다.");
        }
        this.status = PurchaseStatus.CONFIRMED;
    }
}
