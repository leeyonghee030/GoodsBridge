package com.goodsbridge.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 어제까지의 AI 대화 기록을 하루에 한 번, 매일 새벽에 지운다. 오늘 나눈 대화만 남는다.
 * 대화 기록은 중요한 정보가 아니고, 환불·취소 같은 중요한 내역은 교환 내역에 따로 남으므로 지워도 된다.
 * 데모 초기화(04:00)와 겹치지 않게 04:30에 돈다.
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
            log.info("[AI 대화 정리] 어제까지의 대화 {}건을 삭제했습니다.", deleted);
        }
    }
}
