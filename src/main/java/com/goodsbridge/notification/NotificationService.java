package com.goodsbridge.notification;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseService sseService;

    @Transactional(readOnly = true)
    public Long getUnreadCount(Long userId) {
        return notificationRepository.countUnreadNotifications(
            userId,
            Notification.ReadStatus.UNREAD
        );
    }

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {

        Notification notification = notificationRepository
            .findById(notificationId)
            .orElseThrow(() -> new NotFoundException("알림이 없습니다"));

        if (!notification.getReceiver().getId().equals(userId)) {
            throw new ForbiddenException("본인의 알림만 읽음 처리할 수 있습니다");
        }

        notification.markAsRead();
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse.NotificationDto> getUnreadNotifications(Long userId) {

        return notificationRepository
            .findByReceiverIdAndReadStatusOrderByCreatedAtDesc(
                userId,
                Notification.ReadStatus.UNREAD
            )
            .stream()
            .map(NotificationResponse.NotificationDto::new)
            .toList();
    }

    /**
     * 알림을 DB에 저장하고, 해당 사용자가 SSE로 연결되어 있으면 실시간으로 전송합니다.
     *
     * @param userId 알림을 받을 사용자 ID
     * @param type 알림 종류
     * @param title 알림 제목
     * @param content 알림 내용
     * @param referenceType 알림과 연결된 데이터의 종류
     * @param referenceId 알림과 연결된 데이터의 ID
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createNotification(
        Long userId,
        Notification.Type type,
        String title,
        String content,
        Notification.ReferenceType referenceType,
        Long referenceId
    ) {
        // 알림을 받을 사용자를 조회합니다.
        User receiver = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자가 없습니다"));

        // 읽지 않은 상태의 알림 객체를 만듭니다.
        Notification notification = new Notification(
            receiver,
            type,
            title,
            content,
            referenceType,
            referenceId
        );

        // 알림을 데이터베이스에 저장합니다.
        notificationRepository.save(notification);

        // 해당 사용자가 SSE 연결 중이면 저장한 알림을 실시간으로 보냅니다.
        sseService.send(
            userId,
            new NotificationResponse.NotificationDto(notification)
        );
    }
}
