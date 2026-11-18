package com.goodsbridge.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCancelRetryService {
    private static final int MAX_ATTEMPTS = 5; // 결제 취소 최대 횟수 제한

    private final PaymentCancelFailureRepository failureRepository;
    private final PaymentRepository paymentRepository;
    private final PortOnePaymentService portOnePaymentService;

    // 취소 실패 한 건 재시도 (건마다 독립된 트랜잭션)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryOne(Long failureId) {
        PaymentCancelFailure failure = failureRepository.findById(failureId).orElse(null);
        if (failure == null || failure.getStatus() != PaymentCancelFailure.Status.PENDING) {
            return;
        }
        // 그 사이 서버에 정상 기록된 결제는 취소하지 않는다
        if (paymentRepository.existsByPaymentId(failure.getPaymentId())) {
            failure.markDone();
            return;
        }
        try {
            PaymentResponse.PortOnePayment payment = portOnePaymentService.getPayment(failure.getPaymentId());
            // PortOne에서 이미 취소됐거나 승인 상태가 아니면 더 할 일이 없다
            if (payment == null || !"PAID".equals(payment.getStatus())
                || payment.getAmount() == null || payment.getAmount().getTotal() == null) {
                failure.markDone();
                return;
            }
            PaymentResponse.PortOneCancel result = portOnePaymentService
                .cancelPayment(failure.getPaymentId(), payment.getAmount().getTotal(), failure.getReason());
            if (result != null && result.getCancellation() != null
                && "SUCCEEDED".equals(result.getCancellation().getStatus())) {
                failure.markDone();
                log.info("자동 취소 재시도 성공 paymentId={}", failure.getPaymentId());
            } else {
                failure.recordFailure("취소 응답이 SUCCEEDED가 아닙니다.", MAX_ATTEMPTS);
            }
        } catch (RuntimeException e) {
            failure.recordFailure(e.getMessage(), MAX_ATTEMPTS);
            log.error("자동 취소 재시도 실패 paymentId={}", failure.getPaymentId(), e);
        }
    }
}
