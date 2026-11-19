package com.goodsbridge.notification;

import com.goodsbridge.auction.Auction;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.auction.AuctionService;
import com.goodsbridge.auction.Status;
import com.goodsbridge.item.Item;
import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 낙찰자 잔금 결제 기한 임박 알림 (AUCTION_REMAINING_PAYMENT).
 * 기한이 BALANCE_REMINDER_HOURS 안으로 남았고 아직 안 냈으면 한 번만 보낸다.
 */
@ExtendWith(MockitoExtension.class)
class MissingNotificationsTest {

    @Mock AuctionRepository auctionRepository;
    @Mock ApplicationEventPublisher publisher;
    @InjectMocks AuctionService auctionService;

    private Auction wonAuction(LocalDateTime balanceDueAt, String balancePaymentId) {
        Item item = mock(Item.class);
        org.mockito.Mockito.lenient().when(item.getTitle()).thenReturn("한정 포토카드");
        Auction auction = new Auction(null, item, 10_000, LocalDateTime.now().minusHours(1), 1_000);
        User winner = mock(User.class);
        org.mockito.Mockito.lenient().when(winner.getId()).thenReturn(11L);
        ReflectionTestUtils.setField(auction, "status", Status.ENDED_SUCCESS);
        ReflectionTestUtils.setField(auction, "currentWinner", winner);
        ReflectionTestUtils.setField(auction, "currentPrice", 30_000);
        ReflectionTestUtils.setField(auction, "balanceDueAt", balanceDueAt);
        ReflectionTestUtils.setField(auction, "balancePaymentId", balancePaymentId);
        return auction;
    }

    @Test
    void balanceDueSoon_remindsTheWinnerOnce() {
        Auction auction = wonAuction(LocalDateTime.now().plusHours(3), null);
        when(auctionRepository.findByIdLock(7L)).thenReturn(Optional.of(auction));

        auctionService.notifyBalanceDueSoon(7L);
        auctionService.notifyBalanceDueSoon(7L);

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, times(1)).publishEvent(captor.capture());
        NotificationEvent event = captor.getValue();
        assertEquals(11L, event.userId());
        assertEquals(Notification.Type.AUCTION_REMAINING_PAYMENT, event.type());
        assertEquals(Notification.ReferenceType.AUCTION, event.referenceType());
        assertTrue(event.content().contains("29,000원"), event.content()); // 잔금 = 낙찰가 30,000 - 보증금 1,000
        assertTrue(auction.isBalanceReminderNotified());
    }

    @Test
    void balanceDueSoon_skipsWhenAlreadyPaidOrNotYetDue() {
        Auction paid = wonAuction(LocalDateTime.now().plusHours(3), "balance-pay-1");
        when(auctionRepository.findByIdLock(7L)).thenReturn(Optional.of(paid));
        auctionService.notifyBalanceDueSoon(7L);

        Auction farAway = wonAuction(LocalDateTime.now().plusHours(AuctionService.BALANCE_REMINDER_HOURS + 5), null);
        when(auctionRepository.findByIdLock(8L)).thenReturn(Optional.of(farAway));
        auctionService.notifyBalanceDueSoon(8L);

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }
}
