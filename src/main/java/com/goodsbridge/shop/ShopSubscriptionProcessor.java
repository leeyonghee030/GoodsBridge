package com.goodsbridge.shop;

import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopSubscriptionProcessor {

    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ApplicationEventPublisher publisher;

    /**
     * 단일 구독 만료 처리
     *
     * Shop → Subscription 순서로 비관적 락을 획득합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleSubscriptionById(Long subscriptionId) {

        // 1. 구독 ID로 가게 ID 확인
        Long shopId = subscriptionRepository.findShopIdById(subscriptionId)
            .orElse(null);

        if (shopId == null) {
            return false;
        }

        // 2. Shop 비관적 락
        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElse(null);

        if (shop == null || shop.isDeleted()) {
            return false;
        }

        // 3. Subscription 비관적 락
        ShopSubscription subscription =
            subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElse(null);

        if (subscription == null) {
            return false;
        }

        // 4. 구독과 가게 관계 재확인
        if (!shopId.equals(subscription.getShopId())) {
            return false;
        }

        // 5. ACTIVE 상태 확인
        if (subscription.getStatus() != ShopSubscriptionStatus.ACTIVE) {
            return false;
        }

        // 6. 만료일 확인
        LocalDate today = LocalDate.now();

        if (subscription.getEndDate() == null
            || !subscription.getEndDate().isBefore(today)) {
            return false;
        }

        // 7. 만료된 구독 처리
        subscription.expire();

        // 8. 최신 구독인지 확인
        Long latestId = subscriptionRepository
            .findFirstByShopIdOrderByIdDesc(shopId)
            .map(ShopSubscription::getId)
            .orElse(null);

        // 9. 과거 구독이면 이력만 EXPIRED 처리하고 종료
        if (!subscriptionId.equals(latestId)) {
            log.info(
                "과거 구독 만료 이력 정리 완료. subscriptionId={}, shopId={}",
                subscriptionId,
                shopId
            );

            return true;
        }

        // 10. 최신 구독이 만료된 경우에만 이벤트 발행
        eventPublisher.publishEvent(
            new ShopSubscriptionExpiredEvent(shopId)
        );

        publisher.publishEvent(
            new NotificationEvent(
                shop.getOwnerUserId(),
                Notification.Type.SUBSCRIPTION_EXPIRED,
                "[구독 만료]",
                "구독이 만료되어 판매 중인 상품이 중지됐어요. 재구독하면 바로 복구됩니다",
                Notification.ReferenceType.SUBSCRIPTION_SHOP,
                shop.getId()
            )
        );

        log.info(
            "최신 구독 만료 처리 완료. subscriptionId={}, shopId={}",
            subscriptionId,
            shopId
        );

        return true;
    }

    /** 기한이 여전히 D-3인 ACTIVE 구독에 대해 한 번만 만료 임박 알림을 발행한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean sendExpiringReminderById(Long subscriptionId, LocalDate targetDate) {
        Long shopId = subscriptionRepository.findShopIdById(subscriptionId).orElse(null);
        if (shopId == null) return false;

        Shop shop = shopRepository.findByIdWithLock(shopId).orElse(null);
        if (shop == null || shop.isDeleted()) return false;

        ShopSubscription subscription = subscriptionRepository.findByIdWithLock(subscriptionId)
            .orElse(null);
        if (subscription == null
            || !shopId.equals(subscription.getShopId())
            || subscription.getStatus() != ShopSubscriptionStatus.ACTIVE
            || !targetDate.equals(subscription.getEndDate())
            || subscription.isExpiringReminderSent()) {
            return false;
        }

        subscription.markExpiringReminderSent();
        publisher.publishEvent(new NotificationEvent(
            shop.getOwnerUserId(),
            Notification.Type.SUBSCRIPTION_EXPIRING,
            "[구독 만료 임박]",
            "구독이 3일 뒤 만료돼요",
            Notification.ReferenceType.SUBSCRIPTION_SHOP,
            subscriptionId
        ));
        return true;
    }
}
