package com.goodsbridge.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 서버가 켜질 때와 매일 새벽 4시에 시연 데이터를 처음 상태로 맞춘다 (날짜가 항상 오늘 기준이 되도록).
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class DemoAccountInitializer implements ApplicationRunner {

    private final DemoDataService demoDataService;

    @Override
    public void run(ApplicationArguments args) {
        resetSafely();
    }

    // 서버를 오래 켜둬도 마감/방문 날짜가 지나 시연 데이터가 낡지 않게 매일 새벽에 다시 만든다
    @Scheduled(cron = "${app.demo.daily-reset-cron:0 0 4 * * *}")
    public void dailyReset() {
        log.info("시연 데이터를 오늘 기준으로 다시 만듭니다.");
        resetSafely();
    }

    private void resetSafely() {
        try {
            demoDataService.reset();
        } catch (Exception e) {
            // 시연 데이터 준비에 실패해도 서버 자체는 뜨도록 한다
            log.warn("시연 데이터 초기화에 실패했습니다.", e);
        }
    }
}
