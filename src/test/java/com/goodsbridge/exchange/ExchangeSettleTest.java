package com.goodsbridge.exchange;

import com.goodsbridge.exchange.dto.ExchangeCheckInRequest;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExchangeSettleTest {

    @Mock ExchangeRequestRepository exchangeRequestRepository;
    @Mock ExchangeItemRepository exchangeItemRepository;
    @Mock UserRepository userRepository;
    @Mock ShopRepository shopRepository;
    @Mock ItemRepository itemRepository;
    @Mock PaymentService paymentService;
    @Mock PaymentRepository paymentRepository;
    @Mock EntityManager entityManager;
    @Mock ExchangeCommentRepository exchangeCommentRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @InjectMocks ExchangeService exchangeService;

    ExchangeItem exchangeItemA;
    Item itemB;
    Shop shop;
    ExchangeRequest exchangeRequest;

    @BeforeEach
    void setUp() {
        exchangeItemA = mock(ExchangeItem.class);
        itemB = mock(Item.class);
        shop = mock(Shop.class);

        exchangeRequest = ExchangeRequest.builder()
            .exchangeItemA(exchangeItemA)
            .itemB(itemB)
            .message("교환해요")
            .paymentIdB("pay-b")
            .shop(shop)
            .depositAmountB(10_000)
            .build();

        exchangeRequest.updateDepositAmountA(10_000);
        exchangeRequest.updatePaymentIdA("pay-a");
        exchangeRequest.updateStatus(ExchangeRequestStatus.CONFIRMED);
    }

    @Test
    void checkIn_bothShowed_refunds75PercentEach() {
        when(exchangeRequestRepository.findById(100L)).thenReturn(Optional.of(exchangeRequest));
        when(shop.isOwner(1L)).thenReturn(true);
        when(paymentRepository.findByPaymentId("pay-a")).thenReturn(Optional.of(mock(Payment.class)));
        when(paymentRepository.findByPaymentId("pay-b")).thenReturn(Optional.of(mock(Payment.class)));

        ExchangeCheckInRequest request = new ExchangeCheckInRequest();
        ReflectionTestUtils.setField(request, "userAShowed", true);
        ReflectionTestUtils.setField(request, "userBShowed", true);

        exchangeService.checkIn(100L, 1L, request);

        verify(paymentService).refund("pay-a", 7_500, "교환 정산 환불");
        verify(paymentService).refund("pay-b", 7_500, "교환 정산 환불");
        verify(settlementHistoryService, times(2)).calculate(eq(shop), any(Payment.class), eq(2_500), eq(SettlementHistoryType.EXCHANGE_FEE));
        verify(exchangeItemA).updateStatus(ExchangeItemStatus.CLOSED);
    }

    @Test
    void autoClose_bothNoShow_refunds50PercentEachAndReopensItems() {
        Item itemA = mock(Item.class);
        when(exchangeItemA.getItem()).thenReturn(itemA);
        when(exchangeRequestRepository.findByStatusAndDeadlineBefore()).thenReturn(List.of(exchangeRequest));
        when(paymentRepository.findByPaymentId("pay-a")).thenReturn(Optional.of(mock(Payment.class)));
        when(paymentRepository.findByPaymentId("pay-b")).thenReturn(Optional.of(mock(Payment.class)));
        // userAShowed/userBShowed는 생성자 기본값 false 그대로 둠 (둘 다 노쇼)

        exchangeService.autoCloseExchangeEvent();

        assertEquals(ExchangeRequestStatus.EXPIRED_BOTH, exchangeRequest.getStatus());
        verify(paymentService).refund("pay-a", 5_000, "교환 정산 환불");
        verify(paymentService).refund("pay-b", 5_000, "교환 정산 환불");
        verify(settlementHistoryService, times(2)).calculate(eq(shop), any(Payment.class), eq(5_000), eq(SettlementHistoryType.NO_SHOW_FEE));
        verify(exchangeItemA).updateStatus(ExchangeItemStatus.CLOSED);
        verify(itemA).updateStatus(ItemStatus.ON_SALE);
        verify(itemB).updateStatus(ItemStatus.ON_SALE);
    }

    @Test
    void autoClose_onlyAShowed_aRefunded100PercentAndBForfeitsAll() {
        Item itemA = mock(Item.class);
        when(exchangeItemA.getItem()).thenReturn(itemA);
        when(exchangeRequestRepository.findByStatusAndDeadlineBefore()).thenReturn(List.of(exchangeRequest));
        when(paymentRepository.findByPaymentId("pay-a")).thenReturn(Optional.of(mock(Payment.class)));
        when(paymentRepository.findByPaymentId("pay-b")).thenReturn(Optional.of(mock(Payment.class)));
        exchangeRequest.checkInA(true); // A만 출석, B는 노쇼

        exchangeService.autoCloseExchangeEvent();

        assertEquals(ExchangeRequestStatus.EXPIRED_PARTIAL, exchangeRequest.getStatus());
        verify(paymentService).refund("pay-a", 10_000, "교환 정산 환불");
        verify(paymentService, never()).refund(eq("pay-b"), anyInt(), anyString());
        verify(settlementHistoryService).calculate(eq(shop), any(Payment.class), eq(10_000), eq(SettlementHistoryType.NO_SHOW_FEE));
        verify(exchangeItemA).updateStatus(ExchangeItemStatus.CLOSED);
        verify(itemA).updateStatus(ItemStatus.ON_SALE);
        verify(itemB).updateStatus(ItemStatus.ON_SALE);
    }
}
