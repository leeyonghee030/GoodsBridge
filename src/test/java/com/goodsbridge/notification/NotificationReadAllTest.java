package com.goodsbridge.notification;

import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 기획서의 "내 알림 목록(GET /api/notifications/me)"과 "전체 읽음(PATCH /api/notifications/read-all)".
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationReadAllTest {

    @Autowired NotificationService notificationService;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;

    private User user(String email) {
        return userRepository.save(User.builder().email(email).password("pw").nickname(email)
            .loginType(LoginType.EMAIL).build());
    }

    private Notification notify(User receiver, String title) {
        return notificationRepository.save(new Notification(receiver, Notification.Type.BOARD_REPLY, title, "내용",
            Notification.ReferenceType.BOARD, 1L));
    }

    @Test
    void markAllAsRead_readsOnlyMyUnreadNotifications() {
        User me = user("me@noti.test");
        User other = user("other@noti.test");
        notify(me, "1");
        notify(me, "2");
        Notification alreadyRead = notify(me, "3");
        alreadyRead.markAsRead();
        notify(other, "남의 알림");
        notificationRepository.flush();

        assertThat(notificationService.markAllAsRead(me.getId())).isEqualTo(2);
        assertThat(notificationService.getUnreadCount(me.getId())).isZero();
        assertThat(notificationService.getUnreadCount(other.getId())).isEqualTo(1);
        assertThat(notificationService.markAllAsRead(me.getId())).isZero();
    }

    @Test
    void myNotifications_includeReadOnesNewestFirst() {
        User me = user("list@noti.test");
        notify(me, "먼저");
        Notification read = notify(me, "나중");
        read.markAsRead();
        notificationRepository.flush();

        List<String> titles = notificationService.getMyNotifications(me.getId(), PageRequest.of(0, 20))
            .map(NotificationResponse.NotificationDto::title).getContent();
        assertThat(titles).containsExactly("나중", "먼저");
    }
}
