
package com.goodsbridge.shop;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ShopSubscriptionProcessorTest {

    private ShopRepository shopRepository;
    private ShopSubscriptionRepository subscriptionRepository;
    private ApplicationEventPublisher eventPublisher;

    private ShopSubscriptionProcessor processor;

    private Shop shop;
    private ShopSubscription subscription;

    private final Long shopId = 10L;
    private final Long subscriptionId = 100L;

    @BeforeEach
    void setUp() {

        shopRepository = mock(ShopRepository.class);
        subscriptionRepository = mock(ShopSubscriptionRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        processor = new ShopSubscriptionProcessor(
            shopRepository,
            subscriptionRepository,
            eventPublisher
        );

        shop = mock(Shop.class);
        subscription = mock(ShopSubscription.class);
    }

    // 최신 구독 만료
    @Test
    @DisplayName("최신 ACTIVE 구독이 만료되면 EXPIRED 처리하고 이벤트를 발행한다")
    void 최신_구독_만료_처리_test() {

        // given
        givenExpiredSubscription();

        when(subscription.getId()).thenReturn(subscriptionId);

        when(subscriptionRepository.findFirstByShopIdOrderByIdDesc(shopId))
            .thenReturn(Optional.of(subscription));

        // when
        boolean result =
            processor.processSingleSubscriptionById(subscriptionId);

        // then
        assertThat(result).isTrue();

        verify(subscription).expire();

        verify(eventPublisher).publishEvent(
            any(ShopSubscriptionExpiredEvent.class)
        );
    }

    // 과거 구독 만료
    @Test
    @DisplayName("과거 ACTIVE 구독이 만료되면 EXPIRED 처리하고 이벤트는 발행하지 않는다")
    void 과거_구독_만료_처리_test() {

        // given
        givenExpiredSubscription();

        ShopSubscription latestSubscription = mock(ShopSubscription.class);

        when(subscription.getId()).thenReturn(subscriptionId);
        when(latestSubscription.getId()).thenReturn(200L);

        when(subscriptionRepository.findFirstByShopIdOrderByIdDesc(shopId))
            .thenReturn(Optional.of(latestSubscription));

        // when
        boolean result =
            processor.processSingleSubscriptionById(subscriptionId);

        // then
        assertThat(result).isTrue();

        verify(subscription).expire();

        verify(eventPublisher, never()).publishEvent(any());
    }

    // 아직 만료되지 않은 구독
    @Test
    @DisplayName("아직 만료되지 않은 ACTIVE 구독은 처리하지 않는다")
    void 만료되지_않은_구독_test() {

        // given
        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.ACTIVE);

        when(subscription.getEndDate())
            .thenReturn(LocalDate.now());

        // when
        boolean result =
            processor.processSingleSubscriptionById(subscriptionId);

        // then
        assertThat(result).isFalse();

        verify(subscription, never()).expire();

        verify(eventPublisher, never()).publishEvent(any());
    }

    // 이미 EXPIRED 상태
    @Test
    @DisplayName("이미 EXPIRED 상태인 구독은 중복 처리하지 않는다")
    void 이미_만료된_구독_test() {

        // given
        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.EXPIRED);

        // when
        boolean result =
            processor.processSingleSubscriptionById(subscriptionId);

        // then
        assertThat(result).isFalse();

        verify(subscription, never()).expire();

        verify(eventPublisher, never()).publishEvent(any());
    }

    // 가게 조회 실패
    @Test
    @DisplayName("구독에 연결된 가게가 없으면 처리하지 않는다")
    void 가게_조회_실패_test() {

        // given
        when(subscriptionRepository.findShopIdById(subscriptionId))
            .thenReturn(Optional.of(shopId));

        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.empty());

        // when
        boolean result =
            processor.processSingleSubscriptionById(subscriptionId);

        // then
        assertThat(result).isFalse();

        verify(subscriptionRepository, never())
            .findByIdWithLock(subscriptionId);

        verifyNoInteractions(eventPublisher);
    }

    // 구독과 가게 관계 불일치
    @Test
    @DisplayName("구독의 가게 ID가 조회한 가게 ID와 다르면 처리하지 않는다")
    void 구독_가게_관계_불일치_test() {

        // given
        givenShopAndSubscription();

        when(subscription.getShopId()).thenReturn(999L);

        // when
        boolean result =
            processor.processSingleSubscriptionById(subscriptionId);

        // then
        assertThat(result).isFalse();

        verify(subscription, never()).expire();

        verify(eventPublisher, never()).publishEvent(any());
    }

    private void givenShopAndSubscription() {

        when(subscriptionRepository.findShopIdById(subscriptionId))
            .thenReturn(Optional.of(shopId));

        when(shopRepository.findByIdWithLock(shopId))
            .thenReturn(Optional.of(shop));

        when(shop.isDeleted()).thenReturn(false);

        when(subscriptionRepository.findByIdWithLock(subscriptionId))
            .thenReturn(Optional.of(subscription));

        when(subscription.getShopId()).thenReturn(shopId);
    }

    private void givenExpiredSubscription() {

        givenShopAndSubscription();

        when(subscription.getStatus())
            .thenReturn(ShopSubscriptionStatus.ACTIVE);

        when(subscription.getEndDate())
            .thenReturn(LocalDate.now().minusDays(1));
    }
}
