package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 결제 엔티티(Payment)의 누적 환불 한도·상태 전이·지급 처리 규칙.
 *
 * 같은 결제를 동시에 승인하는 상황은 RefundService.approve()의 비관적 락(Refund/Payment 행 잠금)이 막는다.
 * 그 락 자체의 검증은 DB가 필요한 통합 테스트(BidConcurrencyTest와 같은 방식)로 따로 작성해야 하고,
 * 여기서는 락을 통과한 뒤에도 엔티티가 마지막 방어선으로 한도를 지키는지 확인한다.
 */
class PaymentTest {

    private Payment newPayment() {
        return Payment.builder()
            .paymentId("payment-1")
            .user(mock(User.class))
            .amount(10_000)
            .purpose(PaymentPurpose.PURCHASE)
            .build();
    }

    @Test
    void 부분환불하면_누적환불액과_환불가능금액이_바뀌고_부분취소가_된다() {
        Payment payment = newPayment();

        payment.refund(3_000);

        assertEquals(3_000, payment.getRefundedAmount());
        assertEquals(7_000, payment.getRefundableAmount());
        assertEquals(PaymentStatus.PARTIAL_CANCELLED, payment.getStatus());
    }

    @Test
    void 누적환불은_결제금액을_넘을_수_없다() {
        Payment payment = newPayment();

        payment.refund(6_000);
        assertThrows(BadRequestException.class, () -> payment.refund(5_000));
        assertEquals(4_000, payment.getRefundableAmount());
    }

    @Test
    void 남은_금액까지_환불하면_전체취소가_되고_더_환불할_수_없다() {
        Payment payment = newPayment();

        payment.refund(6_000);
        payment.refund(4_000);

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(0, payment.getRefundableAmount());
        assertThrows(BadRequestException.class, () -> payment.refund(1));
    }

    @Test
    void 지급처리된_결제는_환불할_수_없다() {
        Payment payment = newPayment();
        payment.release();

        assertThrows(BadRequestException.class, () -> payment.refund(1_000));
        assertEquals(0, payment.getRefundedAmount());
    }

    @Test
    void 부분환불된_결제도_지급처리할_수_있다() {
        Payment payment = newPayment();
        payment.refund(3_000);

        payment.release();

        assertEquals(PaymentStatus.RELEASED, payment.getStatus());
        assertEquals(3_000, payment.getRefundedAmount());
        assertEquals(7_000, payment.getRefundableAmount());
    }

    @Test
    void 전체환불된_결제는_지급처리할_수_없다() {
        Payment payment = newPayment();
        payment.refund(10_000);

        assertThrows(BadRequestException.class, payment::release);
        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
    }
}
