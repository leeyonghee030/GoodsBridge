package com.goodsbridge.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications/test")
@ConditionalOnProperty(
    name = "app.notifications.test-endpoint-enabled",
    havingValue = "true"
)
public class NotificationTestController {

    private final NotificationService notificationService;

    @PostMapping
    public void sendTestNotification(@AuthenticationPrincipal Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        notificationService.createNotification(
            userId,
            Notification.Type.EXCHANGE_REQUEST,
            "테스트 알림",
            "알림 패널과 실시간 수신을 확인하기 위한 테스트 알림입니다.",
            Notification.ReferenceType.EXCHANGE,
            0L
        );
    }
}
