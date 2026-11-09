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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementHistoryServiceTest {
    @Mock SettlementHistoryRepository settlementHistoryRepository;
    @Mock ShopRepository shopRepository;
    @InjectMocks SettlementHistoryService settlementHistoryService;

    @Test
    void calculate_savesSettlementWhenNew() {
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);
        when(settlementHistoryRepository.existsByPaymentAndType(payment, SettlementHistoryType.AUCTION_FEE))
            .thenReturn(false);

        settlementHistoryService.calculate(shop, payment, 50_000, SettlementHistoryType.AUCTION_FEE);

        ArgumentCaptor<SettlementHistory> captor = ArgumentCaptor.forClass(SettlementHistory.class);
        verify(settlementHistoryRepository).save(captor.capture());
        assertEquals(50_000, captor.getValue().getAmount());
        assertEquals(SettlementHistoryType.AUCTION_FEE, captor.getValue().getType());
    }

    @Test
    void calculate_skipsWhenAmountIsZeroOrNegative() {
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);

        settlementHistoryService.calculate(shop, payment, 0, SettlementHistoryType.NO_SHOW_FEE);
        settlementHistoryService.calculate(shop, payment, -1, SettlementHistoryType.NO_SHOW_FEE);
        settlementHistoryService.calculate(shop, payment, null, SettlementHistoryType.NO_SHOW_FEE);

        verifyNoInteractions(settlementHistoryRepository);
    }

    @Test
    void calculate_isIdempotentForSamePaymentAndType() {
        Shop shop = mock(Shop.class);
        Payment payment = mock(Payment.class);
        when(settlementHistoryRepository.existsByPaymentAndType(payment, SettlementHistoryType.PURCHASE_FEE))
            .thenReturn(true);

        settlementHistoryService.calculate(shop, payment, 30_000, SettlementHistoryType.PURCHASE_FEE);

        verify(settlementHistoryRepository, never()).save(any());
    }

    @Test
    void calculate_rejectsMissingArguments() {
        assertThrows(BadRequestException.class,
            () -> settlementHistoryService.calculate(null, mock(Payment.class), 1_000, SettlementHistoryType.PURCHASE_FEE));
    }

    @Test
    void findByShop_rejectsNonOwner() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(9L)).thenReturn(false);
        when(shopRepository.findById(1L)).thenReturn(Optional.of(shop));

        assertThrows(ForbiddenException.class, () -> settlementHistoryService.findByShop(1L, 9L));
        verifyNoInteractions(settlementHistoryRepository);
    }
}
