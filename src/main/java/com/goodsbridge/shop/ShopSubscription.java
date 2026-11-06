package com.goodsbridge.shop;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "shop_subscription_tb",
    uniqueConstraints = {
        // 복합 유니크 제약 예시: 탈퇴/삭제된 건과 충돌하지 않도록 설계 (또는 비즈니스 정책에 맞게 조정)
        @UniqueConstraint(name = "uk_payment_id", columnNames = {"payment_id"})
    }
)
@SQLRestriction("deleted_at IS NULL") // Hibernate 6+ 표준 소프트 삭제 필터링
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

    @Version // [추가] 동시성 제어(낙관적 락)를 위한 버전 필드
    @Column(nullable = false, columnDefinition = "integer default 0")
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 소프트 삭제 필드

    @Builder
    public ShopSubscription(Long id, Long shopId, String paymentId, Boolean setupFeePaid, LocalDate startDate, LocalDate endDate, ShopSubscriptionStatus status) {
        this.id = id;
        this.shopId = shopId;
        this.paymentId = paymentId; // [추가] 빌더에 paymentId 반영
        this.setupFeePaid = setupFeePaid != null ? setupFeePaid : false;
        this.startDate = startDate;
        this.endDate = endDate;
        // 💡 status가 null로 들어오면 기본값으로 ACTIVE를 부여하여 방어
        this.status = status != null ? status : ShopSubscriptionStatus.ACTIVE;
    }


    // 소프트 삭제 수행 시 유니크 제약 충돌 방지를 위해 paymentId 변형 처리

    public void delete() {
        this.deletedAt = LocalDateTime.now();
        this.status = ShopSubscriptionStatus.EXPIRED;
        if (this.paymentId != null) {
            this.paymentId = this.paymentId + "_DELETED_" + System.currentTimeMillis();
        }
    }

    // 소프트 삭제 복구 메서드
    public void restore(String originalPaymentId) {
        this.deletedAt = null;
        this.status = ShopSubscriptionStatus.ACTIVE;

        // 삭제 시 붙였던 접미사를 제거하고 원본 paymentId 복원
        if (originalPaymentId != null) {
            this.paymentId = originalPaymentId;
        }
    }

    // 기간 연장 시 최신 paymentId도 함께 갱신되도록 반영
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

    //  상태 업데이트 시 paymentId도 함께 갱신되도록 반영
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

    public void updateStatus(ShopSubscriptionStatus status) {
        if (status != null) {
            this.status = status;
        }
    }
}
