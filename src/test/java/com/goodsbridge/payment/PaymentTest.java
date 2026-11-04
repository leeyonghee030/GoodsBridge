package com.goodsbridge.payment;

import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class PaymentTest {

    @Test
    void 결제_객체_생성_확인() {
        // given
        Payment payment = Payment.builder()
            .paymentId("payment-1")
            .user(mock(User.class))
            .amount(10_000)
            .purpose(PaymentPurpose.PURCHASE)
            .build();

        // when & then
        assertEquals("payment-1", payment.getPaymentId());
        assertEquals(10_000, payment.getAmount());
        assertEquals(PaymentPurpose.PURCHASE, payment.getPurpose());
    }
}
