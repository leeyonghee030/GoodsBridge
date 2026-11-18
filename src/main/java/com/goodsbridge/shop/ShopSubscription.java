
package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
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
@Table(
    name = "shop_subscription_tb",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_payment_id",
            columnNames = {"payment_id"}
        )
    }
)
@SQLRestriction("deleted_at IS NULL")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShopSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "payment_id", nullable = false)
    private String paymentId;

    @Column(name = "setup_fee_paid", nullable = false)
    private Boolean setupFeePaid;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopSubscriptionStatus status;

    @Column(name = "expiring_reminder_sent", nullable = false, columnDefinition = "boolean default false")
    private boolean expiringReminderSent = false;

    /**
     * 낙관적 락을 위한 버전 필드
     */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 소프트 삭제 일시
     *
     * null이면 정상 데이터
     * 값이 있으면 삭제된 데이터
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /**
     * 구독이 끝난(취소·만료) 시각. 구독 이탈을 월별로 세는 데 쓴다.
     * 이 컬럼이 생기기 전에 끝난 구독은 null이라 endDate로 대신 본다.
     */
    private LocalDateTime endedAt;

    @Builder
    public ShopSubscription(
        Long id,
        Long shopId,
        String paymentId,
        Boolean setupFeePaid,
        LocalDate startDate,
        LocalDate endDate,
        ShopSubscriptionStatus status
    ) {
        validateSubscriptionPeriod(startDate, endDate);

        this.id = id;
        this.shopId = shopId;
        this.paymentId = paymentId;
        this.setupFeePaid = setupFeePaid != null ? setupFeePaid : false;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status != null
            ? status
            : ShopSubscriptionStatus.ACTIVE;
    }

    /**
     * 구독 기간 유효성 검증
     */
    private void validateSubscriptionPeriod(
        LocalDate startDate,
        LocalDate endDate
    ) {
        if (startDate == null || endDate == null) {
            throw new BadRequestException(
                "구독 시작일과 종료일은 필수입니다."
            );
        }

        if (endDate.isBefore(startDate)) {
            throw new BadRequestException(
                "구독 종료일은 시작일보다 빠를 수 없습니다."
            );
        }
    }

    /**
     * 실제 구독 데이터 소프트 삭제
     *
     * 구독 취소나 만료 처리와는 별개입니다.
     * paymentId는 변경하지 않습니다.
     */
    public void delete() {
        this.deletedAt = LocalDateTime.now();
    }

    /**
     * 기존 구독 기간 연장
     */
    public void extendSubscription(
        LocalDate startDate,
        LocalDate newEndDate,
        Boolean setupFeePaid,
        String paymentId
    ) {
        validateSubscriptionPeriod(startDate, newEndDate);

        this.expiringReminderSent = false;
        this.startDate = startDate;
        this.endDate = newEndDate;

        if (setupFeePaid != null) {
            this.setupFeePaid = setupFeePaid;
        }

        if (paymentId != null && !paymentId.isBlank()) {
            this.paymentId = paymentId;
        }

        this.status = ShopSubscriptionStatus.ACTIVE;
        this.endedAt = null;
    }

    /**
     * 구독 정보 수정
     */
    public void update(
        LocalDate startDate,
        LocalDate endDate,
        Boolean setupFeePaid,
        ShopSubscriptionStatus status,
        String paymentId
    ) {
        validateSubscriptionPeriod(startDate, endDate);

        if (endDate != null && !endDate.equals(this.endDate)) {
            this.expiringReminderSent = false;
        }
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

    /**
     * 구독 상태 변경
     */
    public void updateStatus(ShopSubscriptionStatus status) {
        if (status != null) {
            this.status = status;
        }
    }

    /**
     * 구독 취소
     */
    public void cancel() {
        this.status = ShopSubscriptionStatus.CANCELLED;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * 구독 만료
     */
    public void expire() {
        this.status = ShopSubscriptionStatus.EXPIRED;
        this.endedAt = LocalDateTime.now();
    }

    public void markExpiringReminderSent() {
        this.expiringReminderSent = true;
    }
}
