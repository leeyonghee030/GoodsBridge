package com.goodsbridge.notification;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
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

    // 내 알림 전체 (읽은 것 포함, 최신순)
    @Transactional(readOnly = true)
    public Page<NotificationResponse.NotificationDto> getMyNotifications(
        Long userId, Pageable pageable) {
        return notificationRepository.findAllMine(userId, pageable).map(NotificationResponse.NotificationDto::new);
    }

    // 내 안 읽은 알림을 모두 읽음으로. 바꾼 개수를 돌려준다
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsRead(userId, Notification.ReadStatus.UNREAD, Notification.ReadStatus.READ);
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
        saveAndSend(userId, type, title, content, referenceType, referenceId);
    }

    /**
     * 호출한 쪽이 이미 연 트랜잭션 안에서 알림을 저장한다 (새 DB 연결을 받지 않는다).
     * 묶음 알림(같은 건의 안 읽은 알림 하나를 갱신)을 호출한 쪽이 쥔 잠금 순서대로 갱신해야 할 때 쓴다.
     * 예: 가게의 '이벤트 새 참가' 알림은 이벤트 행 잠금 안에서 갱신해야 동시에 참가해도 판매 수가 뒤바뀌거나 알림이 둘로 갈라지지 않는다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createNotificationInCurrentTransaction(
        Long userId,
        Notification.Type type,
        String title,
        String content,
        Notification.ReferenceType referenceType,
        Long referenceId
    ) {
        // 받을 사람이 없으면 예외를 던지지 않고 건너뛴다. 여기서 예외가 나면 호출한 쪽 업무(참가 등)까지 되돌려진다.
        if (!userRepository.existsById(userId)) {
            log.warn("알림 받을 사용자가 없어 건너뜀 - userId={}, type={}", userId, type);
            return;
        }
        saveAndSend(userId, type, title, content, referenceType, referenceId);
    }

    private void saveAndSend(
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

        Notification notification;
        // 교환 메시지, 경매 새 입찰, 가게의 이벤트 새 참가는 같은 건의 안 읽은 알림 하나를 계속 갱신한다 (알림이 줄줄이 쌓이지 않게)
        // 이벤트 참가 알림 중 참가자 본인 것(참가 완료·번호표, ReferenceType.EVENT)은 구매 건마다 따로 남긴다.
        boolean shopEventJoin = type == Notification.Type.EVENT_JOINED && referenceType == Notification.ReferenceType.SHOP_EVENTS;
        if (type == Notification.Type.EXCHANGE_MESSAGE || type == Notification.Type.AUCTION_BID || shopEventJoin) {
            notification = notificationRepository
                .findFirstByReceiverIdAndTypeAndReferenceTypeAndReferenceIdAndReadStatus(
                    userId,
                    type,
                    referenceType,
                    referenceId,
                    Notification.ReadStatus.UNREAD
                )
                .orElse(null);
            if (notification != null) {
                notification.updateContent(title, content);
            }
        } else {
            notification = null;
        }

        // 같은 교환 건의 읽지 않은 메시지 알림이 없다면 새로 만듭니다.
        if (notification == null) {
            notification = new Notification(receiver, type, title, content, referenceType, referenceId);
        }

        // 알림을 데이터베이스에 저장합니다.
        notificationRepository.save(notification);

        // 커밋된 알림만 실시간으로 보냅니다. 클라이언트가 즉시 조회해도 저장 결과가 보이도록 합니다.
        NotificationResponse.NotificationDto notificationDto =
            new NotificationResponse.NotificationDto(notification);
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 실시간 전송은 덤이다. 실패해도(끊긴 연결 등) 이미 커밋된 알림·업무 처리를 실패로 만들지 않는다
                    try {
                        sseService.send(userId, notificationDto);
                    } catch (RuntimeException e) {
                        log.warn("알림 실시간 전송 실패 - userId={}", userId, e);
                    }
                }
            }
        );
    }
}
