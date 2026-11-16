
package com.goodsbridge.shop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopSubscriptionEventListener {

    private final ShopSubscriptionReconciliationService reconciliationService;

    /**
     * 구독 활성화 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleActivated(ShopSubscriptionActivatedEvent event) {
        reconcile(event.getShopId(), "ACTIVATED");
    }

    /**
     * 구독 만료 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExpired(ShopSubscriptionExpiredEvent event) {
        reconcile(event.getShopId(), "EXPIRED");
    }

    /**
     * 구독 취소 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleCanceled(ShopSubscriptionCanceledEvent event) {
        reconcile(event.getShopId(), "CANCELED");
    }

    /**
     * 최신 구독 상태를 기준으로 상품 상태 보정
     */
    private void reconcile(Long shopId, String eventType) {
        try {
            reconciliationService.reconcileShop(shopId);

            log.info(
                "구독 이벤트 처리 완료. shopId={}, eventType={}",
                shopId,
                eventType
            );

        } catch (Exception e) {
            log.error(
                "구독 이벤트 처리 실패. shopId={}, eventType={}",
                shopId,
                eventType,
                e
            );
        }
    }
}
