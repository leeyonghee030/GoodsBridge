package com.goodsbridge.demo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 서버가 켜질 때마다 시연 데이터를 처음 상태로 맞춘다.
@Component
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class DemoAccountInitializer implements ApplicationRunner {

    private final DemoDataService demoDataService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            demoDataService.reset();
        } catch (Exception e) {
            // 시연 데이터 준비에 실패해도 서버 자체는 뜨도록 한다
            log.warn("시연 데이터 초기화에 실패했습니다.", e);
        }
    }
}
