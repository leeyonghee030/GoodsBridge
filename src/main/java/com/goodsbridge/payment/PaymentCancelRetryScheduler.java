package com.goodsbridge.payment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentCancelRetryScheduler {
    private final PaymentCancelFailureRepository failureRepository;
    private final PaymentCancelRetryService retryService;

    // 5분마다 대기 중인 취소 실패 건을 다시 취소한다
    @Scheduled(fixedDelay = 300_000)
    public void retryPendingCancels() {
        for (PaymentCancelFailure failure
            : failureRepository.findByStatusOrderByIdAsc(PaymentCancelFailure.Status.PENDING)) {
            try {
                retryService.retryOne(failure.getId());
            } catch (RuntimeException e) {
                log.error("자동 취소 재시도 중 오류 failureId={}", failure.getId(), e);
            }
        }
    }
}
