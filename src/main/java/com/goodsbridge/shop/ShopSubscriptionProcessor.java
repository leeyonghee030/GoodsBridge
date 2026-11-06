package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopSubscriptionProcessor {

    private final ItemRepository itemRepository;

    /**
     * 개별 구독 만료 처리 (독립된 트랜잭션으로 실행)
     * REQUIRES_NEW: 호출한 곳의 트랜잭션과 무관하게 새로운 트랜잭션을 열어 실행하므로,
     * 이 메서드 내에서 예외가 나도 호출부의 전체 루프나 다른 건에 영향을 주지 않습니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleSubscription(ShopSubscription subscription) {
        try {
            LocalDateTime now = LocalDateTime.now();
            Long shopId = subscription.getShopId();

            // 1. 구독 소프트 삭제 및 paymentId 유니크 충돌 방지 변형
            subscription.delete();

            // 2. 해당 가게의 모든 연관 상품 일괄 소프트 삭제 및 상태 마감 처리 (CLOSED)
            itemRepository.softDeleteByShopId(shopId, now);

            log.info("[구독 만료 처리 성공] shopId: {}, subscriptionId: {}", shopId, subscription.getId());
            return true;
        } catch (Exception e) {
            log.error("[구독 만료 처리 실패] subscriptionId: {}, error: {}", subscription.getId(), e.getMessage(), e);
            // 개별 실패 시 예외를 던져 현재 트랜잭션만 롤백시키고, 상위 루프에서 캐치하여 전체 배치는 계속 진행
            throw e;
        }
    }
}
