package com.goodsbridge.refund;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 누적 환불 한도(Payment.refund)와 상태 전이를 검증하는 단위 테스트.
 *
 * 주의: 이 테스트는 멀티스레드 테스트가 아니다. 같은 결제를 동시에 승인하는 상황은
 * RefundService.approve()의 비관적 락(Refund/Payment 행 잠금)이 막고,
 * 그 락 자체의 검증은 DB가 필요한 통합 테스트(BidConcurrencyTest와 같은 방식)로 따로 작성해야 한다.
 * 여기서는 락을 통과한 뒤에도 엔티티가 마지막 방어선으로 한도를 지키는지만 확인한다.
 */
class RefundConcurrencyTest {

    private Payment newPayment() {
        return Payment.builder().paymentId("p-1").amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
    }

    @Test
    void repeatedApprovalCannotRefundMoreThanPaymentAmount() {
        Payment payment = newPayment();

        payment.refund(6_000);
        assertThrows(RuntimeException.class, () -> payment.refund(5_000));
        assertEquals(4_000, payment.getRefundableAmount());
    }

    @Test
    void partialRefundMovesStatusToPartialCancelled() {
        Payment payment = newPayment();

        payment.refund(6_000);

        assertEquals(PaymentStatus.PARTIAL_CANCELLED, payment.getStatus());
        assertEquals(6_000, payment.getRefundedAmount());
    }

    @Test
    void refundingTheRemainingAmountMovesStatusToCancelled() {
        Payment payment = newPayment();

        payment.refund(6_000);
        payment.refund(4_000);

        assertEquals(PaymentStatus.CANCELLED, payment.getStatus());
        assertEquals(0, payment.getRefundableAmount());
        assertThrows(RuntimeException.class, () -> payment.refund(1));
    }

    @Test
    void releasedPaymentCannotBeRefundedByEntity() {
        Payment payment = newPayment();
        payment.release();

        assertThrows(RuntimeException.class, () -> payment.refund(1_000));
        assertEquals(0, payment.getRefundedAmount());
    }
}
