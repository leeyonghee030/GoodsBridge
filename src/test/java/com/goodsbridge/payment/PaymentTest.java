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

    @Test
    void 환불_가능_금액은_결제금액에서_누적환불액을_뺀다() {
        Payment payment = Payment.builder()
            .paymentId("payment-1")
            .user(mock(User.class))
            .amount(10_000)
            .purpose(PaymentPurpose.PURCHASE)
            .build();

        payment.refund(3_000);

        assertEquals(7_000, payment.getRefundableAmount());
        assertEquals(PaymentStatus.PARTIAL_CANCELLED, payment.getStatus());
    }


    @Test
    void 부분환불된_결제도_지급처리할_수_있다() {
        Payment payment = Payment.builder()
            .paymentId("payment-1")
            .user(mock(User.class))
            .amount(10_000)
            .purpose(PaymentPurpose.PURCHASE)
            .build();
        payment.refund(3_000);

        payment.release();

        assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        assertEquals(3_000, payment.getRefundedAmount());
        assertEquals(7_000, payment.getRefundableAmount());
    }

    @Test
    void 전체환불된_결제는_지급처리할_수_없다() {
        Payment payment = Payment.builder()
            .paymentId("payment-1")
            .user(mock(User.class))
            .amount(10_000)
            .purpose(PaymentPurpose.PURCHASE)
            .build();
        payment.refund(10_000);

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class, payment::release);
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
    }
}
