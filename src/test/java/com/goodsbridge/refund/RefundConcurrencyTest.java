package com.goodsbridge.refund;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RefundConcurrencyTest {
    @Test
    void repeatedApprovalCannotRefundMoreThanPaymentAmount() {
        Payment payment = Payment.builder().paymentId("p-1").amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();

        payment.refund(6_000);
        assertThrows(RuntimeException.class, () -> payment.refund(5_000));
        assertEquals(4_000, payment.getRefundableAmount());
    }
}
