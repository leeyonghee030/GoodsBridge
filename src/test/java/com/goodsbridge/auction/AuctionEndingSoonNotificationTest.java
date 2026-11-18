package com.goodsbridge.auction;

import com.goodsbridge.item.Item;
import com.goodsbridge.notification.Notification;
import com.goodsbridge.notification.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuctionEndingSoonNotificationTest {
    @Mock AuctionRepository auctionRepository;
    @Mock BidRepository bidRepository;
    @Mock org.springframework.context.ApplicationEventPublisher publisher;
    @InjectMocks AuctionService auctionService;

    @Test
    void notifyEndingSoon_notifiesEveryBidderOnceWithCurrentPrice() {
        Item item = mock(Item.class);
        when(item.getTitle()).thenReturn("한정 포토카드");
        Auction auction = new Auction(null, item, 10_000, LocalDateTime.now().plusMinutes(30), 1_000);
        when(auctionRepository.findByIdLock(7L)).thenReturn(Optional.of(auction));
        when(bidRepository.findBidderUserIds(7L)).thenReturn(List.of(11L, 12L));

        auctionService.notifyEndingSoon(7L);
        auctionService.notifyEndingSoon(7L);

        ArgumentCaptor<NotificationEvent> eventCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(publisher, times(2)).publishEvent(eventCaptor.capture());
        assertEquals(List.of(11L, 12L), eventCaptor.getAllValues().stream()
            .map(NotificationEvent::userId).toList());
        assertEquals(Notification.Type.AUCTION_ENDING_SOON, eventCaptor.getValue().type());
        assertEquals("'한정 포토카드' 경매가 1시간 뒤 마감돼요 (현재가 10,000원)",
            eventCaptor.getValue().content());
        assertEquals(Notification.ReferenceType.AUCTION, eventCaptor.getValue().referenceType());
    }
}
