package com.goodsbridge.chat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatLogCleanupSchedulerTest {

    @Mock
    private ChatLogService chatLogService;
    @InjectMocks
    private ChatLogCleanupScheduler scheduler;

    @Test
    void cleanup_deletesExpiredChatLogs() {
        scheduler.cleanup();

        verify(chatLogService).deleteExpired();
    }

    @Test
    void cleanup_runsEveryDayAtHalfPastFourInTheMorning() throws NoSuchMethodException {
        Scheduled scheduled = ChatLogCleanupScheduler.class.getMethod("cleanup").getAnnotation(Scheduled.class);

        // 04:00의 데모 초기화와 겹치지 않는 시각
        assertThat(scheduled.cron()).isEqualTo("0 30 4 * * *");
    }
}
