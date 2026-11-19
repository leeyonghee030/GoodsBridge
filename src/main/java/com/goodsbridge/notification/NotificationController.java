package com.goodsbridge.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final SseService sseService;
    private final NotificationService notificationService;

    // 읽지 않은 알림 개수 조회
    @GetMapping("/unread/count")
    public Long getUnreadCount(
        @AuthenticationPrincipal Long userId
    ) {
        return notificationService.getUnreadCount(userId);
    }

    // 알림 읽음 처리
    @PatchMapping("/{notificationId}/read")
    public void markAsRead(
        @PathVariable Long notificationId,
        @AuthenticationPrincipal Long userId
    ) {
        notificationService.markAsRead(notificationId, userId);
    }

    // 내 알림 전체 (읽은 것 포함, 최신순). ?page=0&size=20
    @GetMapping("/me")
    public org.springframework.data.domain.Page<NotificationResponse.NotificationDto> getMyNotifications(
        @AuthenticationPrincipal Long userId,
        @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable
    ) {
        return notificationService.getMyNotifications(userId, pageable);
    }

    // 모두 읽음 처리 (바꾼 개수)
    @PatchMapping("/read-all")
    public int markAllAsRead(@AuthenticationPrincipal Long userId) {
        return notificationService.markAllAsRead(userId);
    }

    // 읽지 않은 알림 조회
    @GetMapping("/unread")
    public List<NotificationResponse.NotificationDto> getUnreadNotifications(
        @AuthenticationPrincipal Long userId
    ) {
        return notificationService.getUnreadNotifications(userId);
    }

    // SSE 연결
    @GetMapping(value = "/subscribe", produces = "text/event-stream")
    public SseEmitter subscribe(@AuthenticationPrincipal Long userId) {
        return sseService.connect(userId);
    }
}
