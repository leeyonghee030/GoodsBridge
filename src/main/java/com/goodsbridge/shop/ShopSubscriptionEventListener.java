
package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopSubscriptionEventListener {

    private final ItemRepository itemRepository;
    private final ShopSubscriptionRepository subscriptionRepository;

    /**
     * 최신 구독 상태 확인
     */
    private boolean isLatestSubscriptionStatus(
        Long shopId,
        ShopSubscriptionStatus expectedStatus
    ) {
        return subscriptionRepository
            .findFirstByShopIdOrderByIdDesc(shopId)
            .map(subscription ->
                subscription.getStatus() == expectedStatus
            )
            .orElse(false);
    }

    /**
     * 구독 활성화 이벤트
     *
     * 구독에 의해 중지된 상품만 판매 재개합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionActivated(
        ShopSubscriptionActivatedEvent event
    ) {
        Long shopId = event.getShopId();

        if (!isLatestSubscriptionStatus(
            shopId,
            ShopSubscriptionStatus.ACTIVE
        )) {
            log.info(
                "최신 구독이 ACTIVE 상태가 아니므로 상품 판매 재개를 건너뜁니다. shopId={}",
                shopId
            );
            return;
        }

        int updatedCount = itemRepository.resumeItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE
        );

        log.info(
            "구독 활성화에 따른 상품 판매 재개 완료. shopId={}, updatedCount={}",
            shopId,
            updatedCount
        );
    }

    /**
     * 구독 만료 이벤트
     *
     * 최신 구독이 EXPIRED인 경우에만 상품 판매 중지합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionExpired(
        ShopSubscriptionExpiredEvent event
    ) {
        Long shopId = event.getShopId();

        if (!isLatestSubscriptionStatus(
            shopId,
            ShopSubscriptionStatus.EXPIRED
        )) {
            log.info(
                "최신 구독 상태가 EXPIRED가 아니므로 상품 판매 중지를 건너뜁니다. shopId={}",
                shopId
            );
            return;
        }

        int updatedCount = itemRepository.pauseItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE,
            ItemStatus.CLOSED
        );

        log.info(
            "구독 만료에 따른 상품 판매 중지 완료. shopId={}, updatedCount={}",
            shopId,
            updatedCount
        );
    }

    /**
     * 구독 취소 이벤트
     *
     * 최신 구독이 CANCELLED인 경우에만 상품 판매 중지합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSubscriptionCanceled(
        ShopSubscriptionCanceledEvent event
    ) {
        Long shopId = event.getShopId();

        if (!isLatestSubscriptionStatus(
            shopId,
            ShopSubscriptionStatus.CANCELLED
        )) {
            log.info(
                "최신 구독 상태가 CANCELLED가 아니므로 상품 판매 중지를 건너뜁니다. shopId={}",
                shopId
            );
            return;
        }

        int updatedCount = itemRepository.pauseItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE,
            ItemStatus.CLOSED
        );

        log.info(
            "구독 취소에 따른 상품 판매 중지 완료. shopId={}, updatedCount={}",
            shopId,
            updatedCount
        );
    }
}
