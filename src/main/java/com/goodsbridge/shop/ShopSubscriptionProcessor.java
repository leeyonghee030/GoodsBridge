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

    private final ShopSubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 단일 구독 만료 처리
     *
     * 별도의 트랜잭션(REQUIRES_NEW)에서 실행합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleSubscriptionById(Long subscriptionId) {

        // 1. 구독 조회 및 비관적 락 적용
        ShopSubscription subscription =
            subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElse(null);

        if (subscription == null) {
            log.debug(
                "구독 만료 처리 대상이 존재하지 않습니다. subscriptionId={}",
                subscriptionId
            );
            return false;
        }

        // 2. ACTIVE 상태인 구독만 처리
        if (subscription.getStatus() != ShopSubscriptionStatus.ACTIVE) {
            return false;
        }

        // 3. 구독 종료일 확인
        LocalDate today = LocalDate.now();

        if (subscription.getEndDate() == null
            || !subscription.getEndDate().isBefore(today)) {
            return false;
        }

        // 4. 구독 상태를 EXPIRED로 변경
        Long shopId = subscription.getShopId();

        subscription.expire();

        // 5. 구독 만료 이벤트 발행
        // 실제 상품 판매 중지는 트랜잭션 커밋 이후 실행됩니다.
        eventPublisher.publishEvent(
            new ShopSubscriptionExpiredEvent(shopId)
        );

        log.info(
            "구독 만료 처리 완료. subscriptionId={}, shopId={}",
            subscriptionId,
            shopId
        );

        return true;
    }
}
