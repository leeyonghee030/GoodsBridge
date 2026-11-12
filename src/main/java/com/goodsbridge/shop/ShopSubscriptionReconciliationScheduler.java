package com.goodsbridge.shop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShopSubscriptionReconciliationScheduler {

    private static final int BATCH_SIZE = 100;

    private final ShopRepository shopRepository;
    private final ShopSubscriptionReconciliationService reconciliationService;

    /**
     * 구독 및 상품 판매 상태 정합성 검사
     *
     * 이전 실행 종료 후 5분 뒤 재실행
     */
    @Scheduled(
        fixedDelayString =
            "${app.shop-subscription.reconciliation-delay:300000}"
    )
    public void reconcileAllShops() {

        log.info("[Self-Healing] 구독 정합성 검사 시작");

        Long lastId = 0L;

        int totalCount = 0;
        int successCount = 0;
        int failureCount = 0;

        while (true) {

            // 1. 삭제되지 않은 가게 ID 100개 조회
            List<Long> shopIds =
                shopRepository.findNotDeletedShopIdsAfterId(
                    lastId,
                    PageRequest.of(0, BATCH_SIZE)
                );

            if (shopIds.isEmpty()) {
                break;
            }

            // 2. 가게별 정합성 검사
            for (Long shopId : shopIds) {

                lastId = shopId;
                totalCount++;

                try {
                    reconciliationService.reconcileShop(shopId);
                    successCount++;

                } catch (Exception e) {

                    failureCount++;

                    log.error(
                        "[Self-Healing] 가게 정합성 검사 실패. shopId={}",
                        shopId,
                        e
                    );
                }
            }

            // 3. 마지막 배치가 100개 미만이면 종료
            if (shopIds.size() < BATCH_SIZE) {
                break;
            }
        }

        log.info(
            "[Self-Healing] 구독 정합성 검사 완료. total={}, success={}, failure={}",
            totalCount,
            successCount,
            failureCount
        );
    }
}
