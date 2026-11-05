package com.goodsbridge.shop;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "shop_subscription_tb")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "payment_id", unique = true)
    private String paymentId; // [추가] 결제 ID 저장 필드

    @Column(name = "setup_fee_paid", nullable = false)
    private Boolean setupFeePaid;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopSubscriptionStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    public ShopSubscription(Long id, Long shopId, String paymentId, Boolean setupFeePaid, LocalDate startDate, LocalDate endDate, ShopSubscriptionStatus status) {
        this.id = id;
        this.shopId = shopId;
        this.paymentId = paymentId; // [추가] 빌더에 paymentId 반영
        this.setupFeePaid = setupFeePaid != null ? setupFeePaid : false;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    // [수정] 기간 연장 시 최신 paymentId도 함께 갱신되도록 반영
    public void extendSubscription(LocalDate startDate, LocalDate newEndDate, Boolean setupFeePaid, String paymentId) {
        this.startDate = startDate;
        this.endDate = newEndDate;
        if (setupFeePaid != null) {
            this.setupFeePaid = setupFeePaid;
        }
        if (paymentId != null && !paymentId.isBlank()) {
            this.paymentId = paymentId;
        }
        this.status = ShopSubscriptionStatus.ACTIVE;
    }

    // [수정] 상태 업데이트 시 paymentId도 함께 갱신되도록 반영
    public void update(LocalDate startDate, LocalDate endDate, Boolean setupFeePaid, ShopSubscriptionStatus status, String paymentId) {
        this.startDate = startDate;
        this.endDate = endDate;
        if (setupFeePaid != null) {
            this.setupFeePaid = setupFeePaid;
        }
        if (status != null) {
            this.status = status;
        }
        if (paymentId != null && !paymentId.isBlank()) {
            this.paymentId = paymentId;
        }
    }

    // TODO - 만료 자동 처리 스케줄러나 구독 취소 API 시 사용 예정
    public void updateStatus(ShopSubscriptionStatus status) {
        if (status != null) {
            this.status = status;
        }
    }
}
