package com.goodsbridge.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 보관 기간(30일)이 지난 AI 대화 기록을 매일 새벽에 지운다.
 * 데모 초기화(04:00)와 겹치지 않게 04:30에 돈다. 환불·취소 같은 중요한 내역은 교환 내역에 따로 남으므로 지워도 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatLogCleanupScheduler {

    private final ChatLogService chatLogService;

    @Scheduled(cron = "0 30 4 * * *")
    public void cleanup() {
        int deleted = chatLogService.deleteExpired();
        if (deleted > 0) {
            log.info("[AI 대화 정리] 보관 기간이 지난 대화 {}건을 삭제했습니다.", deleted);
        }
    }
}
