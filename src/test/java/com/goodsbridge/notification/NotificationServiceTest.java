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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;
    @Mock SseService sseService;
    @InjectMocks NotificationService notificationService;

    @Test
    void createNotification_updatesTheUnreadBidNotificationOfTheSameAuctionInsteadOfStackingANewOne() {
        Long shopOwnerId = 50L;
        when(userRepository.findById(shopOwnerId)).thenReturn(Optional.of(new User()));
        Notification earlier = new Notification(new User(), Notification.Type.AUCTION_BID, "[새 입찰]",
            "'포토카드' 경매에 새 입찰이 들어왔어요. 현재가 12,000원 (입찰 1건)", Notification.ReferenceType.AUCTION, 7L);
        when(notificationRepository.findFirstByReceiverIdAndTypeAndReferenceTypeAndReferenceIdAndReadStatus(
            shopOwnerId, Notification.Type.AUCTION_BID, Notification.ReferenceType.AUCTION, 7L, Notification.ReadStatus.UNREAD))
            .thenReturn(Optional.of(earlier));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createNotification(shopOwnerId, Notification.Type.AUCTION_BID, "[새 입찰]",
                "'포토카드' 경매에 새 입찰이 들어왔어요. 현재가 15,000원 (입찰 2건)", Notification.ReferenceType.AUCTION, 7L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // 새 알림을 만들지 않고 기존 안 읽은 알림의 내용을 최신으로 바꾼다
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertSame(earlier, saved.getValue());
        assertEquals("'포토카드' 경매에 새 입찰이 들어왔어요. 현재가 15,000원 (입찰 2건)", earlier.getContent());
    }

    @Test
    void createNotification_updatesTheUnreadShopEventJoinNotificationOfTheSameEventInsteadOfStackingANewOne() {
        Long shopOwnerId = 50L;
        when(userRepository.findById(shopOwnerId)).thenReturn(Optional.of(new User()));
        Notification earlier = new Notification(new User(), Notification.Type.EVENT_JOINED, "[이벤트 새 참가]",
            "'포토카드' 이벤트에 A님이 1개 참가했어요 (1/5)", Notification.ReferenceType.SHOP_EVENTS, 9L);
        when(notificationRepository.findFirstByReceiverIdAndTypeAndReferenceTypeAndReferenceIdAndReadStatus(
            shopOwnerId, Notification.Type.EVENT_JOINED, Notification.ReferenceType.SHOP_EVENTS, 9L, Notification.ReadStatus.UNREAD))
            .thenReturn(Optional.of(earlier));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createNotification(shopOwnerId, Notification.Type.EVENT_JOINED, "[이벤트 새 참가]",
                "'포토카드' 이벤트에 B님이 1개 참가했어요 (2/5)", Notification.ReferenceType.SHOP_EVENTS, 9L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // 가게의 이벤트 새 참가 알림은 같은 이벤트의 안 읽은 알림 하나를 최신 내용으로 갱신한다
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertSame(earlier, saved.getValue());
        assertEquals("'포토카드' 이벤트에 B님이 1개 참가했어요 (2/5)", earlier.getContent());
    }

    @Test
    void createNotification_stacksParticipantEventJoinNotificationsWithoutMerging() {
        Long userId = 60L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createNotification(userId, Notification.Type.EVENT_JOINED, "[이벤트 참가 완료]",
                "'포토카드' 이벤트에 참가했어요. 번호표 1번", Notification.ReferenceType.EVENT, 9L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        // 참가자 본인의 참가 완료 알림(구매 건마다 번호표 안내)은 묶지 않고 새로 만든다
        verify(notificationRepository, never()).findFirstByReceiverIdAndTypeAndReferenceTypeAndReferenceIdAndReadStatus(
            any(), any(), any(), any(), any());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertEquals("[이벤트 참가 완료]", saved.getValue().getTitle());
    }

    @Test
    void createNotification_savesUnreadNotificationAndSendsItOverSse() {
        Long userId = 14L;
        User receiver = new User();
        when(userRepository.findById(userId)).thenReturn(Optional.of(receiver));
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createNotification(
                userId,
                Notification.Type.EXCHANGE_APPROVED,
                "교환 신청 승인",
                "신청한 교환이 승인되었습니다.",
                Notification.ReferenceType.EXCHANGE,
                27L
            );
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

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

    @Test
    void createNotification_sendsOverSseOnlyAfterTransactionCommit() {
        Long userId = 14L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(new User()));
        when(notificationRepository.save(any(Notification.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            notificationService.createNotification(
                userId,
                Notification.Type.EXCHANGE_APPROVED,
                "교환 신청 승인",
                "신청한 교환이 승인되었습니다.",
                Notification.ReferenceType.EXCHANGE,
                27L
            );

            org.mockito.Mockito.verifyNoInteractions(sseService);
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

            verify(sseService).send(org.mockito.ArgumentMatchers.eq(userId), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
