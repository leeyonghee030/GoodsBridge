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
@Table(name = "settlement_history_tb", uniqueConstraints = @UniqueConstraint(name = "uk_settlement_payment_type", columnNames = {"payment_id", "type"}))
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

    private Integer amount; // 가가게 가져가는 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementHistoryType type; // 가게가 가져가는 수수료 타입

    @CreationTimestamp
    private LocalDateTime createdAt;

    public SettlementHistory(Shop shop, Payment payment, Integer amount, SettlementHistoryType type) {
        this.shop = shop;
        this.payment = payment;
        this.amount = amount;
        this.type = type;
    }
}
