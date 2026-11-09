package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
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

    // 결제를 요청한 사용자인지 확인
    public boolean isOwner(Long userId) {
        return this.user.getId().equals(userId);
    }

    // 이미 처리된 결제인지 확인
    public boolean isPaid(){
        return this.status == PaymentStatus.PAID;
    }

    // 검증 통과 후 결제 완료로 변경 처리
    public void markPaid(String pgTxId) {
        this.status = PaymentStatus.PAID;
        this.pgTxId = pgTxId;
    }

    // 취소 처리
    public void markCancelled(){
        this.status = PaymentStatus.CANCELLED;
    }

    public Integer getRefundableAmount() {
        return Math.max(0, amount - refundedAmount);
    }

    public void refund(int refundAmount) {
        if (status == PaymentStatus.CANCELLED) {
            throw new BadRequestException("이미 전체 환불된 결제입니다.");
        }
        if (status != PaymentStatus.PAID
            && status != PaymentStatus.PARTIAL_CANCELLED) {
            throw new BadRequestException("환불 가능한 결제 상태가 아닙니다.");
        }
        if (refundAmount <= 0) {
            throw new BadRequestException("환불 금액은 0원보다 커야 합니다.");
        }
        if (refundAmount > getRefundableAmount()) {
            throw new BadRequestException("환불 가능 금액을 초과했습니다.");
        }
        this.refundedAmount += refundAmount;
        this.status = getRefundableAmount() == 0
            ? PaymentStatus.CANCELLED
            : PaymentStatus.PARTIAL_CANCELLED;
    }

    public void release() {
        if (status == PaymentStatus.RELEASED) return;
        if (status != PaymentStatus.PAID) {
            throw new BadRequestException("Release할 수 없는 결제 상태입니다.");
        }
        this.status = PaymentStatus.RELEASED;
    }
}
