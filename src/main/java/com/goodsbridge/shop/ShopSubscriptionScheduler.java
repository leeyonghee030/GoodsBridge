
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

    @Scheduled(cron = "${scheduler.subscription.expiring-cron:0 0 9 * * *}")
    public void runExpiringReminderScheduler() {
        log.info("[스케줄러 시작] D-3 구독 만료 임박 알림 검사");
        try {
            int sentCount = batchService.sendExpiringSubscriptionReminders();
            log.info("[스케줄러 완료] D-3 구독 알림 {}건 발송", sentCount);
        } catch (Exception e) {
            log.error("[스케줄러 오류] D-3 구독 알림 처리 실패", e);
        }
    }
}
