package com.goodsbridge.demo;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentServiceImpl;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 데모 모드의 "결제 X / 결제 O" 토글: 화면이 만든 demo 결제번호만 생략하고, 실제 결제번호는 PortOne 검증으로 넘긴다
@ExtendWith(MockitoExtension.class)
class DemoPaymentServiceTest {

    @Mock PaymentServiceImpl real;
    @Mock PaymentRepository paymentRepository;
    @Mock EntityManager entityManager;

    DemoPaymentService service;

    @BeforeEach
    void setUp() {
        service = new DemoPaymentService(real, paymentRepository, entityManager);
    }

    @Test
    void subscription_demoPaymentIdSkipsPortOne() {
        when(entityManager.getReference(User.class, 1L)).thenReturn(mock(User.class));

        String result = service.verify("demo-pay-abc", 30_000, PaymentPurpose.SHOP_SUBSCRIPTION, 1L);

        assertEquals("demo-pay-abc", result);
        verify(paymentRepository).save(any(Payment.class));
        verify(real, never()).verify(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void subscription_realPaymentIdGoesToPortOne() {
        when(real.verify("goodsbridge-real-1", 30_000, PaymentPurpose.SHOP_SUBSCRIPTION, 1L)).thenReturn("goodsbridge-real-1");

        String result = service.verify("goodsbridge-real-1", 30_000, PaymentPurpose.SHOP_SUBSCRIPTION, 1L);

        assertEquals("goodsbridge-real-1", result);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void exchangeDeposit_demoPaymentIdIsFaked() {
        when(entityManager.getReference(User.class, 1L)).thenReturn(mock(User.class));

        String result = service.verify("demo-1234", 2_000, PaymentPurpose.EXCHANGE_DEPOSIT, 1L);

        assertTrue(result.startsWith("demo-pay-"));
        verify(paymentRepository).save(any(Payment.class));
        verify(real, never()).verify(any(), org.mockito.ArgumentMatchers.anyInt(), any(), any());
    }

    @Test
    void exchangeDeposit_realPaymentIdGoesToPortOne() {
        when(real.verify("goodsbridge-exchange-1", 2_000, PaymentPurpose.EXCHANGE_DEPOSIT, 1L)).thenReturn("goodsbridge-exchange-1");

        String result = service.verify("goodsbridge-exchange-1", 2_000, PaymentPurpose.EXCHANGE_DEPOSIT, 1L);

        assertEquals("goodsbridge-exchange-1", result);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void exchangeDeposit_missingPaymentIdIsNotFakedAnymore() {
        when(real.verify(null, 2_000, PaymentPurpose.EXCHANGE_DEPOSIT, 1L)).thenReturn("x");

        service.verify(null, 2_000, PaymentPurpose.EXCHANGE_DEPOSIT, 1L);

        verify(paymentRepository, never()).save(any());
    }
}
