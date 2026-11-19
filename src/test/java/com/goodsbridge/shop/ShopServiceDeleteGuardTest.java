package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.event.ShopEventRepository;
import com.goodsbridge.exchange.ExchangeRequestRepository;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopServiceDeleteGuardTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SHOP_ID = 10L;

    @Mock ShopRepository shopRepository;
    @Mock UserRepository userRepository;
    @Mock ItemRepository itemRepository;
    @Mock ShopSubscriptionRepository subscriptionRepository;
    @Mock ShopEventRepository shopEventRepository;
    @Mock ExchangeRequestRepository exchangeRequestRepository;
    @Mock AuctionRepository auctionRepository;
    @Mock PurchaseRepository purchaseRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock ApplicationEventPublisher publisher;

    ShopService shopService;
    Shop shop;

    @BeforeEach
    void setUp() {
        shopService = new ShopService(shopRepository, userRepository, itemRepository,
            subscriptionRepository, shopEventRepository, exchangeRequestRepository,
            auctionRepository, purchaseRepository, eventPublisher, publisher);
        shop = Shop.builder().ownerUserId(OWNER_ID).name("가게").region("서울").address("주소")
            .status(ShopStatus.APPROVED).build();
        when(shopRepository.findByIdWithLock(SHOP_ID)).thenReturn(Optional.of(shop));
    }

    @Test
    void 진행중인_이벤트가_있으면_삭제를_거절한다() {
        when(shopEventRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(true);

        assertRejectedWithoutCleanup();
    }

    @Test
    void 진행중인_교환이_있으면_삭제를_거절한다() {
        when(shopEventRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(exchangeRequestRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(true);

        assertRejectedWithoutCleanup();
    }

    @Test
    void 진행중인_경매가_있으면_삭제를_거절한다() {
        when(shopEventRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(exchangeRequestRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(auctionRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(true);

        assertRejectedWithoutCleanup();
    }

    @Test
    void 배송이_끝나지_않은_구매가_있으면_삭제를_거절한다() {
        when(shopEventRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(exchangeRequestRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(auctionRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(purchaseRepository.existsUndeliveredByShopId(SHOP_ID)).thenReturn(true);

        assertRejectedWithoutCleanup();
    }

    @Test
    void 진행중인게_없으면_기존처럼_삭제한다() {
        when(shopEventRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(exchangeRequestRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);
        when(auctionRepository.existsOngoingByShopId(SHOP_ID)).thenReturn(false);

        shopService.delete(OWNER_ID, SHOP_ID);

        assertThat(shop.isDeleted()).isTrue();
        verify(itemRepository).softDeleteByShopId(any(), any(), any());
    }

    private void assertRejectedWithoutCleanup() {
        assertThatThrownBy(() -> shopService.delete(OWNER_ID, SHOP_ID))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("진행 중인 이벤트·교환·경매·배송");

        assertThat(shop.isDeleted()).isFalse();
        verify(itemRepository, never()).softDeleteByShopId(any(), any(), any());
        verify(subscriptionRepository, never()).findActiveByShopIdWithLock(any(), any(), any());
    }
}
