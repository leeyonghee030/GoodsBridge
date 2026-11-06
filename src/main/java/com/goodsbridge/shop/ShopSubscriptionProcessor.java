package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopSubscriptionProcessor {

    private final ItemRepository itemRepository;
    private final ShopSubscriptionRepository subscriptionRepository;

    /**
     * 개별 구독 만료 처리 (독립된 트랜잭션 실행 및 방어 로직 포함)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleSubscriptionById(Long subscriptionId) {
        try {
            ShopSubscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElse(null);

            if (subscription == null || subscription.getStatus() != ShopSubscriptionStatus.ACTIVE) {
                log.warn("[만료 처리 스킵] 이미 처리되었거나 존재하지 않는 구독 ID: {}", subscriptionId);
                return false;
            }

            LocalDateTime now = LocalDateTime.now();
            Long shopId = subscription.getShopId();

            // 1. 구독 소프트 삭제 및 상태 EXPIRED 변경
            subscription.delete();
            subscriptionRepository.save(subscription);

            // 2. 연관 상품 일괄 소프트 삭제 및 상태 마감 처리 (CLOSED)
            itemRepository.softDeleteByShopId(shopId, now);

            log.info("[구독 만료 처리 성공] shopId: {}, subscriptionId: {}", shopId, subscription.getId());
            return true;
        } catch (Exception e) {
            log.error("[구독 만료 처리 실패] subscriptionId: {}, error: {}", subscriptionId, e.getMessage(), e);
            throw e;
        }
    }
}
