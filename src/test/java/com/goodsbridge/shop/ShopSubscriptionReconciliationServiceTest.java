
package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopSubscriptionReconciliationServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private ShopSubscriptionRepository subscriptionRepository;

    @Mock
    private ItemRepository itemRepository;

    @InjectMocks
    private ShopSubscriptionReconciliationService reconciliationService;

    private final Long shopId = 10L;
    private final Long subscriptionId = 100L;

    private Shop shop;
    private ShopSubscription subscription;

    @BeforeEach
    void setUp() {
        shop = mock(Shop.class);
        subscription = mock(ShopSubscription.class);
    }

    // 공통 가게 및 구독 조회 설정
    private void givenShopAndSubscription() {
        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.of(shop));

        when(shop.isDeleted())
            .thenReturn(false);

        when(subscriptionRepository.findFirstByShopIdOrderByIdDesc(shopId))
            .thenReturn(Optional.of(subscription));

        when(subscription.getId())
            .thenReturn(subscriptionId);

        when(subscriptionRepository.findByIdWithLock(subscriptionId))
            .thenReturn(Optional.of(subscription));

        when(subscription.getShopId())
            .thenReturn(shopId);
    }

    @Test
    @DisplayName("유효한 ACTIVE 구독이면 구독으로 중지된 상품을 재개한다")
    void activeSubscription_test() {
        // given
        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.ACTIVE);

        when(subscription.getEndDate())
            .thenReturn(LocalDate.now().plusDays(30));

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verify(itemRepository).resumeItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE
        );

        verify(itemRepository, never()).pauseItemsBySubscription(
            anyLong(),
            any(),
            any()
        );

        verify(subscription, never()).expire();
    }

    @Test
    @DisplayName("만료일이 지난 ACTIVE 구독이면 구독을 만료시키고 상품을 종료한다")
    void expiredActiveSubscription_test() {
        // given
        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.ACTIVE);

        when(subscription.getEndDate())
            .thenReturn(LocalDate.now().minusDays(1));

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verify(subscription).expire();

        verify(itemRepository).pauseItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE,
            ItemStatus.CLOSED
        );

        verify(itemRepository, never()).resumeItemsBySubscription(
            anyLong(),
            any()
        );
    }

    @Test
    @DisplayName("EXPIRED 구독이면 판매 중인 상품을 종료한다")
    void expiredSubscription_test() {
        // given
        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.EXPIRED);

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verify(itemRepository).pauseItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE,
            ItemStatus.CLOSED
        );

        verify(subscription, never()).expire();
        verify(itemRepository, never()).resumeItemsBySubscription(
            anyLong(),
            any()
        );
    }

    @Test
    @DisplayName("CANCELLED 구독이면 판매 중인 상품을 종료한다")
    void canceledSubscription_test() {
        // given
        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.CANCELLED);

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verify(itemRepository).pauseItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE,
            ItemStatus.CLOSED
        );

        verify(subscription, never()).expire();
        verify(itemRepository, never()).resumeItemsBySubscription(
            anyLong(),
            any()
        );
    }

    @Test
    @DisplayName("삭제된 가게이면 상품 상태를 변경하지 않는다")
    void deletedShop_test() {
        // given
        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.of(shop));

        when(shop.isDeleted())
            .thenReturn(true);

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(itemRepository);
    }

    @Test
    @DisplayName("가게가 존재하지 않으면 상품 상태를 변경하지 않는다")
    void notFoundShop_test() {
        // given
        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.empty());

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verifyNoInteractions(subscriptionRepository);
        verifyNoInteractions(itemRepository);
    }

    @Test
    @DisplayName("구독 이력이 없으면 상품 상태를 변경하지 않는다")
    void noSubscriptionHistory_test() {
        // given
        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.of(shop));

        when(shop.isDeleted())
            .thenReturn(false);

        when(subscriptionRepository.findFirstByShopIdOrderByIdDesc(shopId))
            .thenReturn(Optional.empty());

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verify(subscriptionRepository, never())
            .findByIdWithLock(anyLong());

        verifyNoInteractions(itemRepository);
    }

    @Test
    @DisplayName("ACTIVE 구독의 만료일이 null이면 상품 상태를 변경하지 않는다")
    void activeSubscriptionWithNullEndDate_test() {
        // given
        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.ACTIVE);

        when(subscription.getEndDate())
            .thenReturn(null);

        // when
        reconciliationService.reconcileShop(shopId);

        // then
        verifyNoInteractions(itemRepository);
        verify(subscription, never()).expire();
    }
}
