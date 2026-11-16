
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
            shop, payment, 50_000, 5_000,
            SettlementHistoryType.AUCTION_SALE
        );

        // then
        ArgumentCaptor<SettlementHistory> captor =
            ArgumentCaptor.forClass(SettlementHistory.class);

        verify(settlementHistoryRepository).save(captor.capture());

        SettlementHistory saved = captor.getValue();

        assertEquals(50_000, saved.getAmount());
        assertEquals(5_000, saved.getPlatformFee());
        assertEquals(45_000, saved.getNetAmount());
        assertEquals(SettlementHistoryType.AUCTION_SALE, saved.getType());
    }

    // 플랫폼 수수료 없이 정산 기록 생성
    @Test
    void calculate_savesSettlementWithZeroPlatformFee() {
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
        assertEquals(0, saved.getPlatformFee());
        assertEquals(30_000, saved.getNetAmount());
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
            shop, payment, 30_000, 3_000,
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

    // 플랫폼 수수료가 올바르지 않으면 예외 발생
    @Test
    void calculate_rejectsInvalidPlatformFee() {
        // given
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);

        when(settlementHistoryRepository.existsByPaymentAndType(
            payment, SettlementHistoryType.PURCHASE_FEE
        )).thenReturn(false);

        // when & then
        assertAll(
            // 수수료가 null인 경우
            () -> assertThrows(BadRequestException.class,
                () -> settlementHistoryService.calculate(
                    shop, payment, 50_000, null,
                    SettlementHistoryType.PURCHASE_FEE
                )),

            // 수수료가 음수인 경우
            () -> assertThrows(BadRequestException.class,
                () -> settlementHistoryService.calculate(
                    shop, payment, 50_000, -1,
                    SettlementHistoryType.PURCHASE_FEE
                )),

            // 수수료가 정산 금액보다 큰 경우
            () -> assertThrows(BadRequestException.class,
                () -> settlementHistoryService.calculate(
                    shop, payment, 50_000, 50_001,
                    SettlementHistoryType.PURCHASE_FEE
                ))
        );

        verify(settlementHistoryRepository, never()).save(any());
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
            () -> settlementHistoryService.findByShop(1L, 9L));

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
                LocalDate.of(2026, 11, 10)
            ));

        verifyNoInteractions(settlementHistoryRepository);
    }
}
