package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopSubscriptionReconciliationService {

    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository subscriptionRepository;
    private final ItemRepository itemRepository;

    /**
     * 특정 가게의 구독 상태와 상품 판매 상태를 검사하고 보정합니다.
     *
     * 가게별 독립 트랜잭션으로 실행합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileShop(Long shopId) {

        // 1. 가게 조회 및 비관적 락
        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElse(null);

        if (shop == null || shop.isDeleted()) {
            return;
        }

        // 2. 가장 최근 구독 이력 조회
        ShopSubscription latestSubscription =
            subscriptionRepository
                .findFirstByShopIdOrderByIdDesc(shopId)
                .orElse(null);

        if (latestSubscription == null) {
            log.debug(
                "구독 이력이 없어 정합성 검사를 건너뜁니다. shopId={}",
                shopId
            );
            return;
        }

        // 3. 최신 구독에 비관적 락 적용
        ShopSubscription subscription =
            subscriptionRepository.findByIdWithLock(
                latestSubscription.getId()
            ).orElse(null);

        if (subscription == null) {
            return;
        }

        // 조회한 구독이 해당 가게의 구독인지 재확인
        if (!subscription.getShopId().equals(shopId)) {
            log.error(
                "구독과 가게 정보가 일치하지 않습니다. shopId={}, subscriptionId={}",
                shopId,
                subscription.getId()
            );
            return;
        }

        LocalDate today = LocalDate.now();

        // 4. ACTIVE 구독 처리
        if (subscription.getStatus() == ShopSubscriptionStatus.ACTIVE) {

            // 종료일이 없는 데이터는 자동 보정하지 않고 오류 기록
            if (subscription.getEndDate() == null) {
                log.error(
                    "ACTIVE 구독의 종료일이 없습니다. shopId={}, subscriptionId={}",
                    shopId,
                    subscription.getId()
                );
                return;
            }

            // 만료된 ACTIVE 구독은 EXPIRED로 변경
            if (subscription.getEndDate().isBefore(today)) {

                subscription.expire();

                int updatedCount =
                    itemRepository.pauseItemsBySubscription(
                        shopId,
                        ItemStatus.ON_SALE,
                        ItemStatus.CLOSED
                    );

                log.info(
                    "Self-Healing: 만료 구독 및 상품 판매 중지 보정. shopId={}, subscriptionId={}, updatedCount={}",
                    shopId,
                    subscription.getId(),
                    updatedCount
                );

                return;
            }

            // 유효한 ACTIVE 구독이면 구독으로 중지된 상품만 복구
            int updatedCount =
                itemRepository.resumeItemsBySubscription(
                    shopId,
                    ItemStatus.ON_SALE
                );

            log.info(
                "Self-Healing: 유효한 구독의 상품 판매 재개 보정. shopId={}, updatedCount={}",
                shopId,
                updatedCount
            );

            return;
        }

        // 5. EXPIRED 또는 CANCELLED 구독이면 상품 판매 중지
        int updatedCount =
            itemRepository.pauseItemsBySubscription(
                shopId,
                ItemStatus.ON_SALE,
                ItemStatus.CLOSED
            );

        log.info(
            "Self-Healing: 비활성 구독의 상품 판매 중지 보정. shopId={}, status={}, updatedCount={}",
            shopId,
            subscription.getStatus(),
            updatedCount
        );
    }
}
