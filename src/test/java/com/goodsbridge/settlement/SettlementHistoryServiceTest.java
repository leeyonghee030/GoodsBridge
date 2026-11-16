
package com.goodsbridge.settlement;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementHistoryServiceTest {

    @Mock
    SettlementHistoryRepository settlementHistoryRepository;

    @Mock
    ShopRepository shopRepository;

    @InjectMocks
    SettlementHistoryService settlementHistoryService;

    // 정산 기록 생성 성공
    @Test
    void calculate_savesSettlementWhenNew() {
        // given
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);

        when(settlementHistoryRepository.existsByPaymentAndType(
            payment, SettlementHistoryType.AUCTION_SALE
        )).thenReturn(false);

        // when
        settlementHistoryService.calculate(
            shop, payment, 50_000,
            SettlementHistoryType.AUCTION_SALE
        );

        // then
        ArgumentCaptor<SettlementHistory> captor =
            ArgumentCaptor.forClass(SettlementHistory.class);

        verify(settlementHistoryRepository).save(captor.capture());

        SettlementHistory saved = captor.getValue();

        assertEquals(50_000, saved.getAmount());
        assertEquals(SettlementHistoryType.AUCTION_SALE, saved.getType());
    }

    // 플랫폼 수수료 없이 정산 기록 생성
    @Test
    void calculate_savesSettlementWithoutPlatformFee() {
        // given
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);

        when(settlementHistoryRepository.existsByPaymentAndType(
            payment, SettlementHistoryType.NO_SHOW_FEE
        )).thenReturn(false);

        // when
        settlementHistoryService.calculate(
            shop, payment, 30_000,
            SettlementHistoryType.NO_SHOW_FEE
        );

        // then
        ArgumentCaptor<SettlementHistory> captor =
            ArgumentCaptor.forClass(SettlementHistory.class);

        verify(settlementHistoryRepository).save(captor.capture());

        SettlementHistory saved = captor.getValue();

        assertEquals(30_000, saved.getAmount());
        assertEquals(SettlementHistoryType.NO_SHOW_FEE, saved.getType());
    }

    // 동시 요청으로 unique 제약에 걸리면 500이 아니라 400으로 응답
    @Test
    void calculate_convertsDuplicateKeyViolationToBadRequest() {
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);
        when(settlementHistoryRepository.existsByPaymentAndType(payment, SettlementHistoryType.PURCHASE_FEE))
            .thenReturn(false);
        when(settlementHistoryRepository.save(any(SettlementHistory.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(BadRequestException.class,
            () -> settlementHistoryService.calculate(shop, payment, 10_000, SettlementHistoryType.PURCHASE_FEE));
    }

    // 정산 금액이 0 이하이거나 null이면 저장하지 않음
    @Test
    void calculate_skipsWhenAmountIsZeroOrNegative() {
        // given
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);

        // when
        settlementHistoryService.calculate(
            shop, payment, 0, SettlementHistoryType.NO_SHOW_FEE
        );

        settlementHistoryService.calculate(
            shop, payment, -1, SettlementHistoryType.NO_SHOW_FEE
        );

        settlementHistoryService.calculate(
            shop, payment, null, SettlementHistoryType.NO_SHOW_FEE
        );

        // then
        verifyNoInteractions(settlementHistoryRepository);
    }

    // 동일한 결제와 정산 유형은 중복 저장하지 않음
    @Test
    void calculate_isIdempotentForSamePaymentAndType() {
        // given
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);

        when(settlementHistoryRepository.existsByPaymentAndType(
            payment, SettlementHistoryType.PURCHASE_FEE
        )).thenReturn(true);

        // when
        settlementHistoryService.calculate(
            shop, payment, 30_000,
            SettlementHistoryType.PURCHASE_FEE
        );

        // then
        verify(settlementHistoryRepository)
            .existsByPaymentAndType(payment, SettlementHistoryType.PURCHASE_FEE);

        verify(settlementHistoryRepository, never()).save(any());
    }

    // 정산 생성에 필요한 필수 값 검증
    @Test
    void calculate_rejectsMissingArguments() {
        // given
        Payment payment = mock(Payment.class);
        Shop shop = mock(Shop.class);

        // when & then
        assertAll(
            () -> assertThrows(BadRequestException.class,
                () -> settlementHistoryService.calculate(
                    null, payment, 1_000,
                    SettlementHistoryType.PURCHASE_FEE
                )),
            () -> assertThrows(BadRequestException.class,
                () -> settlementHistoryService.calculate(
                    shop, null, 1_000,
                    SettlementHistoryType.PURCHASE_FEE
                )),
            () -> assertThrows(BadRequestException.class,
                () -> settlementHistoryService.calculate(
                    shop, payment, 1_000, null
                ))
        );

        verifyNoInteractions(settlementHistoryRepository);
    }

    // 본인 소유가 아닌 가게의 정산 내역 조회 차단
    @Test
    void findByShop_rejectsNonOwner() {
        // given
        Shop shop = mock(Shop.class);

        when(shop.isOwner(9L)).thenReturn(false);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));

        // when & then
        assertThrows(ForbiddenException.class,
            () -> settlementHistoryService.findByShop(
                1L, 9L, null, null, null, PageRequest.of(0, 20)));

        verifyNoInteractions(settlementHistoryRepository);
    }

    // 조회 기간의 시작일이 종료일보다 늦으면 예외 발생
    @Test
    void findByShop_rejectsInvalidDateRange() {
        // given
        Shop shop = mock(Shop.class);

        when(shop.isOwner(1L)).thenReturn(true);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));

        // when & then
        assertThrows(BadRequestException.class,
            () -> settlementHistoryService.findByShop(
                1L,
                1L,
                null,
                LocalDate.of(2026, 11, 12),
                LocalDate.of(2026, 11, 10),
                PageRequest.of(0, 20)
            ));

        verifyNoInteractions(settlementHistoryRepository);
    }

    @Test
    void findByShop_usesDatabasePagingAndMapsPage() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(settlementHistoryRepository.findPageByShop(
            eq(1L), isNull(), any(), any(), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(java.util.List.of()));

        var result = settlementHistoryService.findByShop(
            1L, 1L, null, null, null, PageRequest.of(0, 20));

        assertEquals(0, result.getTotalElements());
        verify(settlementHistoryRepository).findPageByShop(
            eq(1L), isNull(), any(), any(), eq(PageRequest.of(0, 20)));
    }

    @Test
    void summarizeByShop_subtractsSubscriptionExpenseOnlyForAllTypes() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(1L)).thenReturn(true);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));
        when(settlementHistoryRepository.summarizeByType(eq(1L), isNull(), any(), any()))
            .thenReturn(java.util.List.<Object[]>of(
                new Object[]{SettlementHistoryType.PURCHASE_FEE, 2L, 10_000L}
            ));
        when(settlementHistoryRepository.sumSubscriptionExpense(eq(1L), any(), any()))
            .thenReturn(3_000L);

        SettlementHistoryResponse.SummaryDto result = settlementHistoryService
            .summarizeByShop(1L, 1L, null, null, null);

        assertEquals(10_000L, result.getGrossRevenue());
        assertEquals(3_000L, result.getSubscriptionExpense());
        assertEquals(7_000L, result.getNetRevenue());
    }
}
