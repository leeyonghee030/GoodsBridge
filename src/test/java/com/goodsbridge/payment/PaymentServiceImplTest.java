package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PortOneClient portOneClient;
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
        when(portOneClient.getPayment("payment-1")).thenReturn(portOnePayment);
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

        verifyNoInteractions(portOneClient, entityManager);
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
        when(portOneClient.getPayment("payment-1"))
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
}
