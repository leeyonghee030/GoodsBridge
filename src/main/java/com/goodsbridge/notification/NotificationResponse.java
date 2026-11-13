package com.goodsbridge.notification;

import java.time.LocalDateTime;

public class NotificationResponse {

    public record NotificationDto(
        Long id,
        String title,
        String content,
        boolean isRead,
        LocalDateTime createdAt
    ) {
        public NotificationDto(Notification notification) {
            this(
                notification.getId(),
                notification.getTitle(),
                notification.getContent(),
                notification.getReadStatus() == Notification.ReadStatus.READ,
                notification.getCreatedAt()
            );
        }
    }
}
