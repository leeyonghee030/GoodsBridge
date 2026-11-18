package com.goodsbridge.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentCancelRetryServiceTest {

    @Mock PaymentCancelFailureRepository failureRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PortOnePaymentService portOnePaymentService;

    @Test
    void retryOne_marksGaveUpAfterFiveFailures() {
        PaymentCancelFailure failure = new PaymentCancelFailure("payment-1", "자동 취소", "첫 실패");
        when(failureRepository.findById(1L)).thenReturn(Optional.of(failure));
        when(paymentRepository.existsByPaymentId("payment-1")).thenReturn(false);
        when(portOnePaymentService.getPayment("payment-1")).thenReturn(paidPortOnePayment(7_000));
        when(portOnePaymentService.cancelPayment(anyString(), anyInt(), anyString()))
            .thenThrow(new RuntimeException("PortOne 취소 실패"));

        PaymentCancelRetryService service = new PaymentCancelRetryService(
            failureRepository, paymentRepository, portOnePaymentService);

        for (int i = 0; i < 5; i++) {
            service.retryOne(1L);
        }

        assertEquals(5, failure.getAttempts());
        assertEquals(PaymentCancelFailure.Status.GAVE_UP, failure.getStatus());
    }

    private static PaymentResponse.PortOnePayment paidPortOnePayment(int total) {
        PaymentResponse.PortOnePayment payment = new PaymentResponse.PortOnePayment();
        payment.setStatus("PAID");
        PaymentResponse.PortOnePayment.Amount amount = new PaymentResponse.PortOnePayment.Amount();
        amount.setTotal(total);
        payment.setAmount(amount);
        return payment;
    }
}
