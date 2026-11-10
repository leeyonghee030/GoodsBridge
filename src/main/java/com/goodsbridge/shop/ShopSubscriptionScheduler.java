package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopSubscriptionScheduler {

    private final ShopSubscriptionRepository subscriptionRepository;
    private final ItemRepository itemRepository;

    private final ShopSubscriptionService shopSubscriptionService;

    /**
     * 설정된 주기에 따라 자동으로 실행되는 구독 만료 스케줄러
     */
    @Scheduled(cron = "${scheduler.subscription.cron:0 0 0 * * *}")
    public void runScheduler() {
        log.info("[스케줄러 시작] 만료된 구독 자동 일괄 검사 수행");
        int processedCount = shopSubscriptionService.processExpiredSubscriptions();
        log.info("[스케줄러 완료] 총 {}건의 만료 구독 처리 완료", processedCount);
    }
}
