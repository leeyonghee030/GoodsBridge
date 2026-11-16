package com.goodsbridge.settlement;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.shop.Shop;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// 가게가 가져가는 수수료
@Entity
@NoArgsConstructor
@Getter
@Table(name = "settlement_history_tb",
    uniqueConstraints = @UniqueConstraint(name = "uk_settlement_payment_type", columnNames = {"payment_id", "type"}),
    indexes = @Index(name = "idx_settlement_shop_created", columnList = "shop_id, created_at"))
public class SettlementHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    private Integer amount; // 가게가 받은 총액 (수수료 차감 전)

    // 처음에는 플랫폼이 수수료를 가져가기로 했지만 구독료만 가져가기로 변경했다.
    // 추후 수수료를 가져가게 하고 싶은 경우 사용한다.
    @Column(name = "platform_fee")
    private Integer platformFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementHistoryType type; // 가게가 가져가는 수수료 타입

    @CreationTimestamp
    private LocalDateTime createdAt;

    public SettlementHistory(Shop shop, Payment payment, Integer amount, SettlementHistoryType type) {
        this(shop, payment, amount, 0, type);
    }

    public SettlementHistory(Shop shop, Payment payment, Integer amount, Integer platformFee, SettlementHistoryType type) {
        this.shop = shop;
        this.payment = payment;
        this.amount = amount;
        this.platformFee = platformFee == null ? 0 : platformFee;
        this.type = type;
    }

    // 수수료를 뺀 가게 실수령액
    public int getNetAmount() {
        int gross = amount == null ? 0 : amount;
        int fee = platformFee == null ? 0 : platformFee;
        return gross - fee;
    }

    // 수수료 (과거 데이터 호환)
    public int getPlatformFeeOrZero() {
        return platformFee == null ? 0 : platformFee;
    }
}
