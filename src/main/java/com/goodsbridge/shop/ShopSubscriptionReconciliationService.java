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
     * <p>
     * 가게별 독립 트랜잭션으로 실행합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcileShop(Long shopId) {

        // 1. Shop 비관적 락
        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElse(null);

        if (shop == null || shop.isDeleted()) {
            return;
        }

        // 2. 해당 가게의 최신 구독 이력 조회
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

        // 3. 최신 구독 이력에 비관적 락
        ShopSubscription subscription =
            subscriptionRepository.findByIdWithLock(
                latestSubscription.getId()
            ).orElse(null);

        if (subscription == null) {
            return;
        }

        // 4. 구독과 가게 관계 재확인
        if (!shopId.equals(subscription.getShopId())) {
            log.error(
                "구독과 가게 정보가 일치하지 않습니다. shopId={}, subscriptionId={}",
                shopId,
                subscription.getId()
            );
            return;
        }

        // 5. 락 획득 후 최신 구독 이력 재확인
        Long latestSubscriptionId =
            subscriptionRepository
                .findFirstByShopIdOrderByIdDesc(shopId)
                .map(ShopSubscription::getId)
                .orElse(null);

        if (!subscription.getId().equals(latestSubscriptionId)) {
            log.debug(
                "최신 구독 이력이 변경되어 정합성 검사를 건너뜁니다. shopId={}",
                shopId
            );
            return;
        }

        LocalDate today = LocalDate.now();

        // 6. ACTIVE 구독 처리
        if (subscription.getStatus() == ShopSubscriptionStatus.ACTIVE) {

            // 만료일이 없는 비정상 데이터
            if (subscription.getEndDate() == null) {
                log.error(
                    "ACTIVE 구독의 만료일이 없습니다. shopId={}, subscriptionId={}",
                    shopId,
                    subscription.getId()
                );
                return;
            }

            // 만료일이 지난 경우
            if (subscription.getEndDate().isBefore(today)) {

                subscription.expire();

                int updatedCount =
                    itemRepository.pauseItemsBySubscription(
                        shopId,
                        ItemStatus.ON_SALE,
                        ItemStatus.CLOSED
                    );

                log.info(
                    "만료된 구독 및 상품 판매 중지 처리 완료. shopId={}, subscriptionId={}, updatedCount={}",
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
                "유효한 구독 확인 및 상품 복구 완료. shopId={}, subscriptionId={}, updatedCount={}",
                shopId,
                subscription.getId(),
                updatedCount
            );

            return;
        }

        // 7. EXPIRED 또는 CANCELLED 구독이면 판매 중지
        int updatedCount =
            itemRepository.pauseItemsBySubscription(
                shopId,
                ItemStatus.ON_SALE,
                ItemStatus.CLOSED
            );

        log.info(
            "비활성 구독 확인 및 상품 판매 중지 완료. shopId={}, subscriptionId={}, status={}, updatedCount={}",
            shopId,
            subscription.getId(),
            subscription.getStatus(),
            updatedCount
        );
    }
}
