package com.goodsbridge.notification;

import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    NotificationRepository notificationRepository;
    UserRepository userRepository;

    // 알림 생성

    /**
     * 알림을 생성하고 저장합니다.
     *
     * @param receiverId 알림을 받을 사용자 ID
     * @param type 알림 유형 (교환 신청, 교환 승인, 경매 입찰 등)
     * @param title 알림 제목
     * @param content 알림 내용
     * @param referenceType 알림과 연결된 데이터 유형 (교환, 경매 등)
     * @param referenceId 알림과 연결된 데이터의 ID
     */
    public void createNotification(
        Long receiverId,
        Notification.Type type,
        String title,
        String content,
        Notification.ReferenceType referenceType,
        Long referenceId) {
        Notification notification = new Notification(
            userRepository.findById(receiverId).orElseThrow(() -> new NotFoundException("수신자를 찾을 수가 없습니다")),
            type,
            title,
            content,
            referenceType,
            referenceId);

        notificationRepository.save(notification);
    }

    // 알림 호출
}
