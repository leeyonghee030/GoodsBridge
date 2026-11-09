package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PortOnePaymentService portOnePaymentService;
    @Mock EntityManager entityManager;
    @InjectMocks PaymentServiceImpl paymentService;

    @Test
    void verify_savesPaidPaymentWhenPortOnePaymentMatches() {
        PaymentResponse.PortOnePayment portOnePayment = new PaymentResponse.PortOnePayment();
        portOnePayment.setStatus("PAID");
        portOnePayment.setPgTxId("pg-1");
        PaymentResponse.PortOnePayment.Amount amount = new PaymentResponse.PortOnePayment.Amount();
        amount.setTotal(10_000);
        portOnePayment.setAmount(amount);

        User user = mock(User.class);
        when(paymentRepository.existsByPaymentId("payment-1")).thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1")).thenReturn(portOnePayment);
        when(entityManager.getReference(User.class, 3L)).thenReturn(user);

        String result = paymentService.verify("payment-1", 10_000, PaymentPurpose.PURCHASE, 3L);

        assertEquals("payment-1", result);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void verify_rejectsDuplicatePayment() {
        when(paymentRepository.existsByPaymentId("payment-1")).thenReturn(true);

        assertThrows(BadRequestException.class,
            () -> paymentService.verify("payment-1", 10_000, PaymentPurpose.PURCHASE, 3L));

        verifyNoInteractions(portOnePaymentService, entityManager);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void verify_rejectsWhenPortOneAmountDoesNotMatchExpectedAmount() {
        PaymentResponse.PortOnePayment portOnePayment =
            new PaymentResponse.PortOnePayment();

        portOnePayment.setStatus("PAID");

        PaymentResponse.PortOnePayment.Amount amount =
            new PaymentResponse.PortOnePayment.Amount();
        amount.setTotal(9_000);

        portOnePayment.setAmount(amount);

        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(portOnePayment);

        assertThrows(
            BadRequestException.class,
            () -> paymentService.verify(
                "payment-1",
                10_000,
                PaymentPurpose.PURCHASE,
                3L
            )
        );

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void release_marksPaidPaymentAsReleased() {
        Payment payment = Payment.builder().paymentId("payment-1")
            .user(mock(User.class)).amount(10_000).purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("payment-1")).thenReturn(java.util.Optional.of(payment));

        paymentService.release("payment-1");

        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        verifyNoInteractions(portOnePaymentService);
    }

    @Test
    void release_isIdempotentForAlreadyReleasedPayment() {
        Payment payment = Payment.builder().paymentId("payment-1")
            .user(mock(User.class)).amount(10_000).purpose(PaymentPurpose.PURCHASE).build();
        payment.release();
        when(paymentRepository.findByPaymentIdLock("payment-1")).thenReturn(java.util.Optional.of(payment));

        paymentService.release("payment-1");

        org.junit.jupiter.api.Assertions.assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        verifyNoInteractions(portOnePaymentService);
    }

    @Test
    void verify_convertsUniqueViolationOnSaveToBadRequest() {
        PaymentResponse.PortOnePayment portOnePayment = new PaymentResponse.PortOnePayment();
        portOnePayment.setStatus("PAID");
        portOnePayment.setPgTxId("pg-1");
        PaymentResponse.PortOnePayment.Amount amount = new PaymentResponse.PortOnePayment.Amount();
        amount.setTotal(10_000);
        portOnePayment.setAmount(amount);

        when(paymentRepository.existsByPaymentId("payment-1")).thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1")).thenReturn(portOnePayment);
        when(entityManager.getReference(User.class, 3L)).thenReturn(mock(User.class));
        // exists 검사를 동시에 통과한 중복 요청 -> DB unique 제약 위반
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException("dup"));

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> paymentService.verify("payment-1", 10_000, PaymentPurpose.PURCHASE, 3L));

        assertEquals("이미 처리된 결제입니다.", e.getMessage());
    }

    @Test
    void refund_locksPaymentAndAppliesAmountOnlyAfterPortOneSucceeds() {
        Payment payment = Payment.builder().paymentId("payment-1")
            .user(mock(User.class)).amount(10_000).purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("payment-1")).thenReturn(java.util.Optional.of(payment));

        PaymentResponse.PortOneCancel cancel = new PaymentResponse.PortOneCancel();
        PaymentResponse.PortOneCancel.Cancellation cancellation = new PaymentResponse.PortOneCancel.Cancellation();
        cancellation.setStatus("SUCCEEDED");
        cancel.setCancellation(cancellation);
        when(portOnePaymentService.cancelPayment("payment-1", 4_000, "사유")).thenReturn(cancel);

        paymentService.refund("payment-1", 4_000, "사유");

        assertEquals(4_000, payment.getRefundedAmount());
        assertEquals(PaymentStatus.PARTIAL_CANCELLED, payment.getStatus());
        verify(paymentRepository).findByPaymentIdLock("payment-1");
    }

    @Test
    void refund_doesNotChangePaymentWhenPortOneCancelFails() {
        Payment payment = Payment.builder().paymentId("payment-1")
            .user(mock(User.class)).amount(10_000).purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("payment-1")).thenReturn(java.util.Optional.of(payment));

        PaymentResponse.PortOneCancel cancel = new PaymentResponse.PortOneCancel();
        PaymentResponse.PortOneCancel.Cancellation cancellation = new PaymentResponse.PortOneCancel.Cancellation();
        cancellation.setStatus("FAILED");
        cancel.setCancellation(cancellation);
        when(portOnePaymentService.cancelPayment("payment-1", 4_000, "사유")).thenReturn(cancel);

        assertThrows(BadRequestException.class, () -> paymentService.refund("payment-1", 4_000, "사유"));

        assertEquals(0, payment.getRefundedAmount());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
    }

    @Test
    void refund_rejectsAmountAboveRefundableWithoutCallingPortOne() {
        Payment payment = Payment.builder().paymentId("payment-1")
            .user(mock(User.class)).amount(10_000).purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("payment-1")).thenReturn(java.util.Optional.of(payment));

        assertThrows(BadRequestException.class, () -> paymentService.refund("payment-1", 10_001, "사유"));

        verifyNoInteractions(portOnePaymentService);
    }
}
