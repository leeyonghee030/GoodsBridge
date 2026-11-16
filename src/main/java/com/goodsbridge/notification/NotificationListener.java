package com.goodsbridge.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationListener {

    private final NotificationService notificationService;

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true
    )
    public void handle(NotificationEvent event) {

        notificationService.createNotification(
            event.userId(),
            event.type(),
            event.title(),
            event.content(),
            event.referenceType(),
            event.referenceId()
        );
    }
}
