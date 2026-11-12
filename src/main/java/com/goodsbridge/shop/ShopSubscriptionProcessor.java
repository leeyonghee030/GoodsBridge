package com.goodsbridge.shop;

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

        log.info(
            "최신 구독 만료 처리 완료. subscriptionId={}, shopId={}",
            subscriptionId,
            shopId
        );

        return true;
    }
}
