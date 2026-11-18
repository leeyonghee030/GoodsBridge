package com.goodsbridge.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findFirstByReceiverIdAndTypeAndReferenceTypeAndReferenceIdAndReadStatus(
        Long receiverId,
        Notification.Type type,
        Notification.ReferenceType referenceType,
        Long referenceId,
        Notification.ReadStatus readStatus
    );

    // 읽지 않은 알림 개수 조회
    @Query("""
    SELECT COUNT(n)
    FROM Notification n
    WHERE n.receiver.id = :userId
    AND n.readStatus = :readStatus
""")
    Long countUnreadNotifications(Long userId, Notification.ReadStatus readStatus);

    @Query("""
    SELECT n
    FROM Notification n
    WHERE n.receiver.id = :userId
      AND n.readStatus = :readStatus
    ORDER BY n.createdAt DESC
    """)
    List<Notification> findByReceiverIdAndReadStatusOrderByCreatedAtDesc(Long userId, Notification.ReadStatus readStatus);
}
