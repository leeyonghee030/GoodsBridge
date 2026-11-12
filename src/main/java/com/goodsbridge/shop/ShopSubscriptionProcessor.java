package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopSubscriptionProcessor {

    private final ItemRepository itemRepository;
    private final ShopSubscriptionRepository subscriptionRepository;

    /**
     * 개별 구독 만료 처리 (비관적 락을 통한 갱신 작업 직렬화 및 최신 만료일 재검증)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processSingleSubscriptionById(Long subscriptionId) {
        try {
            // 💡 1. 비관적 락(PESSIMISTIC_WRITE)을 걸어 구독 연장(결제) 작업과 직렬화합니다.
            ShopSubscription subscription = subscriptionRepository.findByIdWithLock(subscriptionId)
                .orElse(null);

            if (subscription == null || subscription.getStatus() != ShopSubscriptionStatus.ACTIVE) {
                log.warn("[만료 처리 스킵] 이미 처리되었거나 존재하지 않는 구독 ID: {}", subscriptionId);
                return false;
            }

            // 💡 2. 락 획득 후 최신 endDate가 실제로 만료되었는지(오늘 이전인지) 재검증
            // (배치 조회 후 사용자가 결제를 통해 구독을 연장했을 경우, 상품이 닫히는 사고 방지)
            LocalDate today = LocalDate.now();
            if (subscription.getEndDate() == null || !subscription.getEndDate().isBefore(today)) {
                log.info("[만료 처리 스킵] 사용자에 의해 구독이 연장되어 아직 유효한 구독 ID: {} (만료일: {})",
                    subscriptionId, subscription.getEndDate());
                return false;
            }

            LocalDateTime now = LocalDateTime.now();
            Long shopId = subscription.getShopId();

            // 3. 구독 소프트 삭제 및 상태 EXPIRED 변경
            subscription.delete();
            subscriptionRepository.save(subscription);

            // 4. 판매중인 상품만 소프트 삭제 및 마감 처리 (CLOSED). 판매완료 상품은 배송/환불 처리를 위해 유지
            itemRepository.closeOnSaleByShopId(shopId, now);

            log.info("[구독 만료 처리 성공] shopId: {}, subscriptionId: {}", shopId, subscription.getId());
            return true;
        } catch (Exception e) {
            log.error("[구독 만료 처리 실패] subscriptionId: {}, error: {}", subscriptionId, e.getMessage(), e);
            throw e;
        }
    }
}
