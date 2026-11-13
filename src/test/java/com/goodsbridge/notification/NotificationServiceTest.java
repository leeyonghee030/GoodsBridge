package com.goodsbridge.notification;

import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock SseService sseService;
    @InjectMocks NotificationService notificationService;

    @Test
    void createNotification_savesUnreadNotificationAndSendsItOverSse() {
        Long userId = 14L;
        User receiver = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(receiver));
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.createNotification(
            userId,
            Notification.Type.EXCHANGE_APPROVED,
            "교환 신청 승인",
            "신청한 교환이 승인되었습니다.",
            Notification.ReferenceType.EXCHANGE,
            27L
        );

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        Notification savedNotification = notificationCaptor.getValue();
        assertSame(receiver, savedNotification.getReceiver());
        assertEquals(Notification.Type.EXCHANGE_APPROVED, savedNotification.getType());
        assertEquals("교환 신청 승인", savedNotification.getTitle());
        assertEquals("신청한 교환이 승인되었습니다.", savedNotification.getContent());
        assertEquals(Notification.ReferenceType.EXCHANGE, savedNotification.getReferenceType());
        assertEquals(27L, savedNotification.getReferenceId());
        assertEquals(Notification.ReadStatus.UNREAD, savedNotification.getReadStatus());

        ArgumentCaptor<NotificationResponse.NotificationDto> dtoCaptor =
            ArgumentCaptor.forClass(NotificationResponse.NotificationDto.class);
        verify(sseService).send(org.mockito.ArgumentMatchers.eq(userId), dtoCaptor.capture());
        NotificationResponse.NotificationDto sentDto = dtoCaptor.getValue();
        assertEquals("교환 신청 승인", sentDto.title());
        assertEquals("신청한 교환이 승인되었습니다.", sentDto.content());
        assertFalse(sentDto.isRead());
    }
}
