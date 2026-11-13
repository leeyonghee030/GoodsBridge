package com.goodsbridge.notification;

public record NotificationEvent(
    Long userId,
    Notification.Type type,
    String title,
    String content,
    Notification.ReferenceType referenceType,
    Long referenceId
) {
}
