package com.goodsbridge.refund;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.user.User;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

@Entity
@Table(name = "refund_tb")
@Getter
@NoArgsConstructor
public class Refund {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_user_id", nullable = false)
    private User user;

    private String reason;      // 사용자의 환불 사유

    private Integer requestedAmount;    // 환불 금액

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;          // 환불 상태

    @CreationTimestamp
    private Timestamp createdAt;    // 환불 요청 시간

    @UpdateTimestamp
    private Timestamp updatedAt;    // 승인, 거절 시간

    @Column(length = 500)
    private String rejectReason;    // 관리자 거절 사유

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RequestedBy requestedBy;    // 유저/가게 환불 신청 구분

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundType type;

    @Builder
    public Refund(Payment payment, User user, String reason, Integer
        requestedAmount, Status status, RequestedBy requestedBy, RefundType type) {
        this.payment = payment;
        this.user = user;
        this.reason = reason;
        this.requestedAmount = requestedAmount;
        this.status = status;
        this.requestedBy = requestedBy;
        this.type = type;
    }

    // 관리자가 아직 처리하지 않은 요청인지 확인
    public boolean isPending() {
        return this.status == Status.REQUESTED;
    }

    // 승인
    public void approve() {
        if (status == Status.APPROVED) {
            throw new BadRequestException("이미 승인된 환불 요청입니다.");
        }
        if (status != Status.REQUESTED) {
            throw new BadRequestException("처리 대기 중인 환불만 승인할 수 있습니다.");
        }
        this.status = Status.APPROVED;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }

    // 거절
    public void reject(String rejectReason) {
        if (status != Status.REQUESTED) {
            throw new BadRequestException("처리 대기 중인 환불만 거절할 수 있습니다.");
        }
        if (rejectReason == null || rejectReason.isBlank()) {
            throw new BadRequestException("거절 사유는 필수입니다.");
        }
        if (rejectReason.length() > 500) {
            throw new BadRequestException("거절 사유는 500자 이하로 입력해주세요.");
        }
        this.rejectReason = rejectReason;
        this.status = Status.REJECTED;
        this.updatedAt = new Timestamp(System.currentTimeMillis());
    }
}
