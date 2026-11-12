
package com.goodsbridge.shop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class ShopSubscriptionEventListenerTest {

    @Mock
    private ShopSubscriptionReconciliationService reconciliationService;

    @InjectMocks
    private ShopSubscriptionEventListener eventListener;

    private final Long shopId = 10L;

    @Test
    @DisplayName("구독 활성화 이벤트가 발생하면 가게의 상품 상태를 보정한다")
    void handleActivated_test() {
        // given
        ShopSubscriptionActivatedEvent event =
            new ShopSubscriptionActivatedEvent(shopId);

        // when
        eventListener.handleActivated(event);

        // then
        verify(reconciliationService, times(1))
            .reconcileShop(shopId);
    }

    @Test
    @DisplayName("구독 만료 이벤트가 발생하면 가게의 상품 상태를 보정한다")
    void handleExpired_test() {
        // given
        ShopSubscriptionExpiredEvent event =
            new ShopSubscriptionExpiredEvent(shopId);

        // when
        eventListener.handleExpired(event);

        // then
        verify(reconciliationService, times(1))
            .reconcileShop(shopId);
    }

    @Test
    @DisplayName("구독 취소 이벤트가 발생하면 가게의 상품 상태를 보정한다")
    void handleCanceled_test() {
        // given
        ShopSubscriptionCanceledEvent event =
            new ShopSubscriptionCanceledEvent(shopId);

        // when
        eventListener.handleCanceled(event);

        // then
        verify(reconciliationService, times(1))
            .reconcileShop(shopId);
    }
}
