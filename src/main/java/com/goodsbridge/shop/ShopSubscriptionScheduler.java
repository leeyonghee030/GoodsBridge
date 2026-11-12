
package com.goodsbridge.shop;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopSubscriptionScheduler {

    private final ShopSubscriptionBatchService batchService;

    @Scheduled(cron = "${scheduler.subscription.cron:0 0 0 * * *}")
    public void runScheduler() {

        log.info("[스케줄러 시작] 만료된 구독 자동 일괄 검사 수행");

        try {
            int processedCount = batchService.processExpiredSubscriptions();

            log.info(
                "[스케줄러 완료] 총 {}건의 만료 구독 처리 완료",
                processedCount
            );

        } catch (Exception e) {
            log.error("[스케줄러 오류] 만료 구독 일괄 처리 실패", e);
        }
    }
}
