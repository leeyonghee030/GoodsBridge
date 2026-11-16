package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.InternalServerException;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PortOnePaymentService portOnePaymentService;
    @Mock EntityManager entityManager;
    @Mock PaymentCancelFailureRepository paymentCancelFailureRepository;
    @InjectMocks PaymentServiceImpl paymentService;

    @Test
    void verify_savesPaidPaymentWhenPortOnePaymentMatches() {
        PaymentResponse.PortOnePayment portOnePayment = new PaymentResponse.PortOnePayment();
        portOnePayment.setStatus("PAID");
        portOnePayment.setPgTxId("pg-1");

        PaymentResponse.PortOnePayment.Amount amount =
            new PaymentResponse.PortOnePayment.Amount();
        amount.setTotal(10_000);
        portOnePayment.setAmount(amount);

        User user = mock(User.class);

        when(paymentRepository.existsByPaymentId("payment-1")).thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1")).thenReturn(portOnePayment);
        when(entityManager.getReference(User.class, 3L)).thenReturn(user);

        String result = paymentService.verify(
            "payment-1", 10_000, PaymentPurpose.PURCHASE, 3L
        );

        assertEquals("payment-1", result);
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void verify_rejectsDuplicatePayment() {
        when(paymentRepository.existsByPaymentId("payment-1")).thenReturn(true);

        assertThrows(BadRequestException.class,
            () -> paymentService.verify(
                "payment-1", 10_000, PaymentPurpose.PURCHASE, 3L
            )
        );

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
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));

        paymentService.release("payment-1");

        assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        verifyNoInteractions(portOnePaymentService);
    }

    @Test
    void release_isIdempotentForAlreadyReleasedPayment() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        payment.release();

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));

        paymentService.release("payment-1");

        assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        verifyNoInteractions(portOnePaymentService);
    }

    @Test
    void verify_convertsUniqueViolationOnSaveToBadRequest() {
        PaymentResponse.PortOnePayment portOnePayment =
            new PaymentResponse.PortOnePayment();

        portOnePayment.setStatus("PAID");
        portOnePayment.setPgTxId("pg-1");

        PaymentResponse.PortOnePayment.Amount amount =
            new PaymentResponse.PortOnePayment.Amount();
        amount.setTotal(10_000);
        portOnePayment.setAmount(amount);

        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(portOnePayment);
        when(entityManager.getReference(User.class, 3L))
            .thenReturn(mock(User.class));

        // exists 검사를 동시에 통과한 중복 요청 -> DB unique 제약 위반
        when(paymentRepository.save(any(Payment.class)))
            .thenThrow(new DataIntegrityViolationException("dup"));

        BadRequestException e = assertThrows(
            BadRequestException.class,
            () -> paymentService.verify(
                "payment-1", 10_000, PaymentPurpose.PURCHASE, 3L
            )
        );

        assertEquals("이미 처리된 결제입니다.", e.getMessage());
    }

    @Test
    void refund_locksPaymentAndAppliesAmountOnlyAfterPortOneSucceeds() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(paidPortOnePayment(10_000));
        when(portOnePaymentService.cancelPayment("payment-1", 4_000, "사유"))
            .thenReturn(succeededCancel());

        paymentService.refund("payment-1", 4_000, "사유");

        assertEquals(4_000, payment.getRefundedAmount());
        assertEquals(PaymentStatus.PARTIAL_CANCELLED, payment.getStatus());

        verify(paymentRepository).findByPaymentIdLock("payment-1");
    }

    @Test
    void refund_doesNotChangePaymentWhenPortOneCancelFails() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(paidPortOnePayment(10_000));

        PaymentResponse.PortOneCancel cancel = new PaymentResponse.PortOneCancel();
        PaymentResponse.PortOneCancel.Cancellation cancellation =
            new PaymentResponse.PortOneCancel.Cancellation();
        cancellation.setStatus("FAILED");
        cancel.setCancellation(cancellation);

        when(portOnePaymentService.cancelPayment("payment-1", 4_000, "사유"))
            .thenReturn(cancel);

        assertThrows(
            BadRequestException.class,
            () -> paymentService.refund("payment-1", 4_000, "사유")
        );

        assertEquals(0, payment.getRefundedAmount());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
    }

    @Test
    void refund_rejectsAmountAboveRefundableWithoutCallingPortOne() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));

        assertThrows(
            BadRequestException.class,
            () -> paymentService.refund("payment-1", 10_001, "사유")
        );

        verifyNoInteractions(portOnePaymentService);
    }

    @Test
    void refund_syncsDatabaseWhenPortOneAlreadyCancelled() {
        Payment payment = newPayment(PaymentPurpose.AUCTION_BALANCE);

        PaymentResponse.PortOnePayment portOnePayment = paidPortOnePayment(10_000);
        portOnePayment.setStatus("CANCELLED");

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(portOnePayment);

        paymentService.refund("payment-1", 10_000, "경매 환불");

        assertEquals(10_000, payment.getRefundedAmount());
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());

        verify(portOnePaymentService, never())
            .cancelPayment(any(), anyInt(), any());
    }

    @Test
    void refund_rejectsWhenPortOneAlreadyCancelledButAmountDiffersFromRefundable() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        PaymentResponse.PortOnePayment portOnePayment = paidPortOnePayment(10_000);
        portOnePayment.setStatus("CANCELLED");

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(portOnePayment);

        assertThrows(
            BadRequestException.class,
            () -> paymentService.refund("payment-1", 4_000, "사유")
        );

        assertEquals(0, payment.getRefundedAmount());
        assertEquals(PaymentStatus.PAID, payment.getStatus());
        verify(portOnePaymentService, never())
            .cancelPayment(any(), anyInt(), any());
    }

    @Test
    void refund_rejectsWhenPortOnePaymentIsNotInRefundableState() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        PaymentResponse.PortOnePayment portOnePayment = paidPortOnePayment(10_000);
        portOnePayment.setStatus("READY");

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(portOnePayment);

        assertThrows(
            BadRequestException.class,
            () -> paymentService.refund("payment-1", 4_000, "사유")
        );

        assertEquals(0, payment.getRefundedAmount());
        verify(portOnePaymentService, never())
            .cancelPayment(any(), anyInt(), any());
    }

    @Test
    void refund_rejectsWhenPortOnePaymentIsNotFound() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(null);

        assertThrows(
            BadRequestException.class,
            () -> paymentService.refund("payment-1", 4_000, "사유")
        );

        assertEquals(0, payment.getRefundedAmount());
        verify(portOnePaymentService, never())
            .cancelPayment(any(), anyInt(), any());
    }

    @Test
    void release_allowsPartiallyCancelledPayment() {
        Payment payment = newPayment(PaymentPurpose.PURCHASE);

        payment.refund(3_000);

        when(paymentRepository.findByPaymentIdLock("payment-1"))
            .thenReturn(Optional.of(payment));

        paymentService.release("payment-1");

        assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        verifyNoInteractions(portOnePaymentService);
    }

    @Test
    void cancelUnrecorded_cancelsPaidPortOnePaymentThatWasNeverRecorded() {
        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(paidPortOnePayment(7_000));
        when(portOnePaymentService.cancelPayment("payment-1", 7_000, "자동 취소"))
            .thenReturn(succeededCancel());

        paymentService.cancelUnrecorded("payment-1", "자동 취소");

        verify(portOnePaymentService)
            .cancelPayment("payment-1", 7_000, "자동 취소");
        verifyNoInteractions(paymentCancelFailureRepository);
    }

    @Test
    void cancelUnrecorded_neverCancelsAnAlreadyRecordedPayment() {
        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(true);

        paymentService.cancelUnrecorded("payment-1", "자동 취소");

        verifyNoInteractions(portOnePaymentService);
    }

    @Test
    void cancelUnrecorded_skipsPaymentThatIsNotPaidOnPortOne() {
        PaymentResponse.PortOnePayment portOnePayment = paidPortOnePayment(7_000);
        portOnePayment.setStatus("CANCELLED");

        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(portOnePayment);

        paymentService.cancelUnrecorded("payment-1", "자동 취소");

        verify(portOnePaymentService, never())
            .cancelPayment(any(), anyInt(), any());
    }

    @Test
    void cancelUnrecorded_doesNothingForBlankPaymentId() {
        paymentService.cancelUnrecorded(" ", "자동 취소");
        paymentService.cancelUnrecorded(null, "자동 취소");

        verifyNoInteractions(paymentRepository, portOnePaymentService);
    }

    @Test
    void cancelUnrecorded_doesNotThrowAndRecordsFailureWhenPortOneCancelFails() {
        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(paidPortOnePayment(7_000));
        when(portOnePaymentService.cancelPayment("payment-1", 7_000, "자동 취소"))
            .thenThrow(new InternalServerException("결제 서버에 연결할 수 없습니다."));
        when(paymentCancelFailureRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);

        // 원래 구매 실패 예외가 가려지지 않도록 예외를 삼키고, 재시도용 실패 기록만 남긴다.
        assertDoesNotThrow(
            () -> paymentService.cancelUnrecorded("payment-1", "자동 취소")
        );

        verify(paymentCancelFailureRepository).save(any(PaymentCancelFailure.class));
    }

    @Test
    void cancelUnrecorded_recordsFailureWhenCancelResponseIsNotSucceeded() {
        PaymentResponse.PortOneCancel cancel = new PaymentResponse.PortOneCancel();
        PaymentResponse.PortOneCancel.Cancellation cancellation =
            new PaymentResponse.PortOneCancel.Cancellation();
        cancellation.setStatus("FAILED");
        cancel.setCancellation(cancellation);

        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(paidPortOnePayment(7_000));
        when(portOnePaymentService.cancelPayment("payment-1", 7_000, "자동 취소"))
            .thenReturn(cancel);
        when(paymentCancelFailureRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);

        paymentService.cancelUnrecorded("payment-1", "자동 취소");

        verify(paymentCancelFailureRepository).save(any(PaymentCancelFailure.class));
    }

    @Test
    void cancelUnrecorded_doesNotRecordDuplicateFailure() {
        when(paymentRepository.existsByPaymentId("payment-1"))
            .thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1"))
            .thenReturn(paidPortOnePayment(7_000));
        when(portOnePaymentService.cancelPayment("payment-1", 7_000, "자동 취소"))
            .thenThrow(new InternalServerException("결제 서버에 연결할 수 없습니다."));
        when(paymentCancelFailureRepository.existsByPaymentId("payment-1"))
            .thenReturn(true);

        paymentService.cancelUnrecorded("payment-1", "자동 취소");

        verify(paymentCancelFailureRepository, never()).save(any());
    }

    private static Payment newPayment(PaymentPurpose purpose) {
        return Payment.builder()
            .paymentId("payment-1")
            .user(mock(User.class))
            .amount(10_000)
            .purpose(purpose)
            .build();
    }

    private static PaymentResponse.PortOnePayment paidPortOnePayment(int total) {
        PaymentResponse.PortOnePayment portOnePayment =
            new PaymentResponse.PortOnePayment();

        portOnePayment.setStatus("PAID");
        portOnePayment.setPgTxId("pg-1");

        PaymentResponse.PortOnePayment.Amount amount =
            new PaymentResponse.PortOnePayment.Amount();
        amount.setTotal(total);

        portOnePayment.setAmount(amount);

        return portOnePayment;
    }

    private static PaymentResponse.PortOneCancel succeededCancel() {
        PaymentResponse.PortOneCancel cancel =
            new PaymentResponse.PortOneCancel();

        PaymentResponse.PortOneCancel.Cancellation cancellation =
            new PaymentResponse.PortOneCancel.Cancellation();

        cancellation.setStatus("SUCCEEDED");
        cancel.setCancellation(cancellation);

        return cancel;
    }
}
