
package com.goodsbridge.shop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShopSubscriptionBatchServiceTest {

    @Mock
    private ShopSubscriptionRepository subscriptionRepository;

    @Mock
    private ShopSubscriptionProcessor subscriptionProcessor;

    @InjectMocks
    private ShopSubscriptionBatchService batchService;

    @Test
    void 만료된_구독을_정상적으로_일괄_처리한다() {

        // given
        List<Long> subscriptionIds = List.of(1L, 2L, 3L);

        when(subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
            eq(0L),
            any(LocalDate.class),
            eq(ShopSubscriptionStatus.ACTIVE),
            eq(PageRequest.of(0, 100))
        )).thenReturn(subscriptionIds);

        when(subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
            eq(3L),
            any(LocalDate.class),
            eq(ShopSubscriptionStatus.ACTIVE),
            eq(PageRequest.of(0, 100))
        )).thenReturn(List.of());

        when(subscriptionProcessor.processSingleSubscriptionById(anyLong()))
            .thenReturn(true);

        // when
        int result = batchService.processExpiredSubscriptions();

        // then
        assertThat(result).isEqualTo(3);

        verify(subscriptionProcessor).processSingleSubscriptionById(1L);
        verify(subscriptionProcessor).processSingleSubscriptionById(2L);
        verify(subscriptionProcessor).processSingleSubscriptionById(3L);

        verify(subscriptionRepository, times(2))
            .findExpiredActiveSubscriptionIdsAfterId(
                anyLong(),
                any(LocalDate.class),
                eq(ShopSubscriptionStatus.ACTIVE),
                eq(PageRequest.of(0, 100))
            );
    }

    @Test
    void 특정_구독_처리에_실패해도_나머지_구독을_계속_처리한다() {

        // given
        List<Long> subscriptionIds = List.of(1L, 2L, 3L);

        when(subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
            eq(0L),
            any(LocalDate.class),
            eq(ShopSubscriptionStatus.ACTIVE),
            eq(PageRequest.of(0, 100))
        )).thenReturn(subscriptionIds);

        when(subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
            eq(3L),
            any(LocalDate.class),
            eq(ShopSubscriptionStatus.ACTIVE),
            eq(PageRequest.of(0, 100))
        )).thenReturn(List.of());

        when(subscriptionProcessor.processSingleSubscriptionById(1L))
            .thenReturn(true);

        when(subscriptionProcessor.processSingleSubscriptionById(2L))
            .thenThrow(new RuntimeException("구독 처리 오류"));

        when(subscriptionProcessor.processSingleSubscriptionById(3L))
            .thenReturn(true);

        // when
        int result = batchService.processExpiredSubscriptions();

        // then
        assertThat(result).isEqualTo(2);

        verify(subscriptionProcessor).processSingleSubscriptionById(1L);
        verify(subscriptionProcessor).processSingleSubscriptionById(2L);
        verify(subscriptionProcessor).processSingleSubscriptionById(3L);
    }

    @Test
    void 만료된_구독이_없으면_처리하지_않는다() {

        // given
        when(subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
            eq(0L),
            any(LocalDate.class),
            eq(ShopSubscriptionStatus.ACTIVE),
            eq(PageRequest.of(0, 100))
        )).thenReturn(List.of());

        // when
        int result = batchService.processExpiredSubscriptions();

        // then
        assertThat(result).isZero();

        verify(subscriptionProcessor, never())
            .processSingleSubscriptionById(anyLong());
    }
}
