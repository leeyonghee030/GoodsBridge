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
    public ShopSubscription(Long id, Long shopId, Boolean setupFeePaid, LocalDate startDate, LocalDate endDate,ShopSubscriptionStatus status) {
        this.shopId = shopId;
        this.setupFeePaid = setupFeePaid != null ? setupFeePaid : false;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    public void extendSubscription(LocalDate startDate, LocalDate newEndDate ,Boolean setupFeePaid) {
        this.startDate = startDate;
        this.endDate = newEndDate;
        if (setupFeePaid != null) {
            this.setupFeePaid = setupFeePaid;
        }
        this.status = ShopSubscriptionStatus.ACTIVE;
    }

    public void update(LocalDate startDate, LocalDate endDate, Boolean setupFeePaid, ShopSubscriptionStatus status) {
        this.startDate = startDate;
        this.endDate = endDate;
        if (setupFeePaid != null) {
            this.setupFeePaid = setupFeePaid;
        }
        if (status != null) {
            this.status = status;
        }
    }

    public void updateStatus(ShopSubscriptionStatus status) {
        if (status != null) {
            this.status = status;
        }
    }
}
