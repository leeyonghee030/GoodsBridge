
package com.goodsbridge.shop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.LongStream;

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


        when(subscriptionProcessor.processSingleSubscriptionById(anyLong()))
            .thenReturn(true);

        // when
        int result = batchService.processExpiredSubscriptions();

        // then
        assertThat(result).isEqualTo(3);

        verify(subscriptionProcessor).processSingleSubscriptionById(1L);
        verify(subscriptionProcessor).processSingleSubscriptionById(2L);
        verify(subscriptionProcessor).processSingleSubscriptionById(3L);

        verify(subscriptionRepository, times(1))
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
    @Test
    void 만료된_구독이_100건을_초과하면_커서_방식으로_다음_페이지를_조회한다() {

        // given
        // 첫 번째 페이지: 1번 ~ 100번
        List<Long> firstPageIds = LongStream.rangeClosed(1, 100)
            .boxed()
            .toList();

        // 두 번째 페이지: 101번
        List<Long> secondPageIds = List.of(101L);

        when(subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
            eq(0L),
            any(LocalDate.class),
            eq(ShopSubscriptionStatus.ACTIVE),
            eq(PageRequest.of(0, 100))
        )).thenReturn(firstPageIds);

        when(subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
            eq(100L),
            any(LocalDate.class),
            eq(ShopSubscriptionStatus.ACTIVE),
            eq(PageRequest.of(0, 100))
        )).thenReturn(secondPageIds);

        when(subscriptionProcessor.processSingleSubscriptionById(anyLong()))
            .thenReturn(true);

        // when
        int result = batchService.processExpiredSubscriptions();

        // then
        assertThat(result).isEqualTo(101);

        // 첫 번째 페이지와 두 번째 페이지의 데이터가 모두 처리되었는지 확인
        verify(subscriptionProcessor, times(101))
            .processSingleSubscriptionById(anyLong());

        verify(subscriptionProcessor).processSingleSubscriptionById(1L);
        verify(subscriptionProcessor).processSingleSubscriptionById(100L);
        verify(subscriptionProcessor).processSingleSubscriptionById(101L);

        // Repository가 두 번 조회되었는지 확인
        verify(subscriptionRepository, times(1))
            .findExpiredActiveSubscriptionIdsAfterId(
                eq(0L),
                any(LocalDate.class),
                eq(ShopSubscriptionStatus.ACTIVE),
                eq(PageRequest.of(0, 100))
            );

        verify(subscriptionRepository, times(1))
            .findExpiredActiveSubscriptionIdsAfterId(
                eq(100L),
                any(LocalDate.class),
                eq(ShopSubscriptionStatus.ACTIVE),
                eq(PageRequest.of(0, 100))
            );

        verifyNoMoreInteractions(subscriptionRepository);
    }
}
