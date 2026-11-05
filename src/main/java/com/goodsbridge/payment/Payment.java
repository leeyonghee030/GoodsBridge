package com.goodsbridge.payment;

import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_tb")
@Getter
@NoArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;    // 사용자 PK

    @Column(nullable = false, unique = true)
    private String paymentId;   // PG 거래 식별자

    private String pgTxId;  // PG id

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;  // 사용자 FK

    private Integer amount;     // 승인된 전체 금액

    @Column(nullable = false)
    private Integer refundedAmount = 0; // 누적 부분 환불액 (최초 0원)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentPurpose purpose; // 결제 형태(구매, 예치, 이벤트, 경매금)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;   // 결제 상태

    @CreationTimestamp
    private LocalDateTime createdAt;

    @Builder
    public Payment(String paymentId, String pgTxId, User user, Integer amount, PaymentPurpose purpose) {
        this.paymentId = paymentId;
        this.pgTxId = pgTxId;
        this.user = user;
        this.amount = amount;
        this.purpose = purpose;
        this.refundedAmount = 0;
        this.status = PaymentStatus.PAID;
    }
}
