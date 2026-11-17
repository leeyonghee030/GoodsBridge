package com.goodsbridge.shop;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

// 관리자 대시보드의 구독 이탈(월별)이 쓰는 구독 종료 시각 기록 규칙
class ShopSubscriptionEndedAtTest {

    private ShopSubscription active() {
        return ShopSubscription.builder().shopId(1L).paymentId("pay-1").setupFeePaid(true)
            .startDate(LocalDate.now()).endDate(LocalDate.now().plusDays(30))
            .status(ShopSubscriptionStatus.ACTIVE).build();
    }

    @Test
    void activeSubscription_hasNoEndedAt() {
        assertThat(active().getEndedAt()).isNull();
    }

    @Test
    void cancel_recordsEndedAt() {
        ShopSubscription subscription = active();

        subscription.cancel();

        assertThat(subscription.getStatus()).isEqualTo(ShopSubscriptionStatus.CANCELLED);
        assertThat(subscription.getEndedAt()).isNotNull();
    }

    @Test
    void expire_recordsEndedAt() {
        ShopSubscription subscription = active();

        subscription.expire();

        assertThat(subscription.getEndedAt()).isNotNull();
    }

    @Test
    void extending_clearsEndedAt() {
        ShopSubscription subscription = active();
        subscription.expire();

        subscription.extendSubscription(LocalDate.now(), LocalDate.now().plusDays(30), true, "pay-2");

        assertThat(subscription.getStatus()).isEqualTo(ShopSubscriptionStatus.ACTIVE);
        assertThat(subscription.getEndedAt()).isNull();
    }
}
