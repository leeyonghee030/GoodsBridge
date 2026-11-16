package com.goodsbridge.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 결제마다 남기는 가게 구독 결제 이력. */
@Getter
@Entity
@Table(
    name = "shop_subscription_payment_tb",
    uniqueConstraints = @UniqueConstraint(name = "uk_subscription_payment_id", columnNames = "payment_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopSubscriptionPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ShopSubscriptionPayment(Long shopId, String paymentId) {
        this.shopId = shopId;
        this.paymentId = paymentId;
    }
}
