package com.goodsbridge.payment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// 서버 거절 후 PortOne 자동 취소에 실패한 결제 (스케줄러가 다시 취소를 시도한다)
@Entity
@Getter
@NoArgsConstructor
@Table(name = "payment_cancel_failure_tb",
    uniqueConstraints = @UniqueConstraint(name = "uk_cancel_failure_payment", columnNames = "payment_id"))
public class PaymentCancelFailure {
    public enum Status { PENDING, DONE, GAVE_UP }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(nullable = false, length = 200)
    private String reason;  // 결제 취소 이유

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    private int attempts;

    @Column(length = 500)
    private String lastError;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // 결제 취소 실패
    public PaymentCancelFailure(String paymentId, String reason, String error) {
        this.paymentId = paymentId;
        this.reason = reason == null ? "" : reason;
        this.lastError = trim(error);
    }

    // 취소 재시도 실패를 기록하고, 횟수가 상한을 넘으면 포기 상태로 둔다
    public void recordFailure(String error, int maxAttempts) {
        this.attempts++;
        this.lastError = trim(error);
        if (attempts >= maxAttempts) {
            this.status = Status.GAVE_UP;
        }
    }

    public void markDone() {
        this.status = Status.DONE;
    }

    private static String trim(String text) {
        if (text == null) return null;
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
