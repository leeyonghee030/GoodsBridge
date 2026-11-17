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

    private Integer amount; // 가게가 받은 총액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementHistoryType type; // 가게 정산 금액

    @CreationTimestamp
    private LocalDateTime createdAt;

    public SettlementHistory(Shop shop, Payment payment, Integer amount, SettlementHistoryType type) {
        this.shop = shop;
        this.payment = payment;
        this.amount = amount;
        this.type = type;
    }
}
