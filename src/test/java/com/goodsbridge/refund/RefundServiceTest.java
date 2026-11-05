package com.goodsbridge.refund;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {
    @Mock RefundRepository refundRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentService paymentService;
    @Mock UserRepository userRepository;
    @Mock ShopRepository shopRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @Mock PurchaseRepository purchaseRepository;
    @InjectMocks RefundService refundService;

    @Test
    void request_setsPurchaseTypeAndRejectsAmountAboveRemainingBalance() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        payment.refund(3_000);

        RefundRequest request = request("p-1", 8_000, "상품 환불");
        when(paymentRepository.findByPaymentId("p-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> refundService.request(1L, request));
        verifyNoInteractions(refundRepository);
    }

    @Test
    void request_persistsPurchaseRefundType() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        RefundRequest request = request("p-1", 3_000, "상품 환불");
        when(paymentRepository.findByPaymentId("p-1")).thenReturn(Optional.of(payment));
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        refundService.request(1L, request);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(captor.capture());
        assertEquals(RefundType.PURCHASE, captor.getValue().getType());
    }

    @Test
    void approve_rejectsNonAdmin() {
        User user = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(user.getRole()).thenReturn(Role.USER);

        assertThrows(ForbiddenException.class, () -> refundService.approve(1L, 7L));
        verifyNoInteractions(paymentService);
    }

    private static RefundRequest request(String paymentId, int amount, String reason) {
        RefundRequest request = new RefundRequest();
        ReflectionTestUtils.setField(request, "paymentId", paymentId);
        ReflectionTestUtils.setField(request, "requestedAmount", amount);
        ReflectionTestUtils.setField(request, "reason", reason);
        return request;
    }
}
