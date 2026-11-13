package com.goodsbridge.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock NotificationService notificationService;

    @Test
    void handle_forwardsPublishedEventToNotificationService() {
        NotificationListener listener = new NotificationListener(notificationService);
        NotificationEvent event = new NotificationEvent(
            14L,
            Notification.Type.EXCHANGE_APPROVED,
            "교환 신청 승인",
            "신청한 교환이 승인되었습니다.",
            Notification.ReferenceType.EXCHANGE,
            27L
        );

        listener.handle(event);

        verify(notificationService).createNotification(
            14L,
            Notification.Type.EXCHANGE_APPROVED,
            "교환 신청 승인",
            "신청한 교환이 승인되었습니다.",
            Notification.ReferenceType.EXCHANGE,
            27L
        );
    }
}
