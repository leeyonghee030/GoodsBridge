package com.goodsbridge.demo;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentResponse;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.payment.PaymentServiceImpl;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

// TODO(데모 종료 후 제거): 데모 모드에서는 "교환 보증금" 결제를 PortOne 없이 통과시킨다.
// 다른 결제 목적(구매/이벤트/경매/구독)은 실제 PaymentServiceImpl 로 그대로 위임한다.
// 가짜 결제도 payment_tb 에 PAID 로 저장해서 정산(SettlementHistory)이 결제 행을 참조할 수 있게 한다.
@Slf4j
@Primary
@Service
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoPaymentService implements PaymentService {

    private static final String DEMO_PREFIX = "demo-pay-";

    private final PaymentServiceImpl real;
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) {
        // TODO(데모 종료 후 제거): 상품 구매도 화면이 만든 demo-pay- 결제번호면 PortOne 없이 통과시킨다.
        // PurchaseService 는 요청의 paymentId 로 구매를 저장하므로, 같은 번호로 결제 행을 만들어야 구매/정산/환불이 연결된다.
        if (purpose == PaymentPurpose.PURCHASE && paymentId != null && paymentId.startsWith(DEMO_PREFIX)) {
            saveDemoPayment(paymentId, expectedAmount, purpose, userId);
            log.info("[DEMO] 상품 구매 결제 생략 - paymentId={}, userId={}, amount={}", paymentId, userId, expectedAmount);
            return paymentId;
        }
        if (purpose != PaymentPurpose.EXCHANGE_DEPOSIT) {
            return real.verify(paymentId, expectedAmount, purpose, userId);
        }
        String demoPaymentId = DEMO_PREFIX + UUID.randomUUID();
        saveDemoPayment(demoPaymentId, expectedAmount, purpose, userId);
        log.info("[DEMO] 교환 보증금 결제 생략 - paymentId={}, userId={}, amount={}", demoPaymentId, userId, expectedAmount);
        return demoPaymentId;
    }

    // PortOne 없이 결제 완료(PAID) 행만 저장한다 (정산/환불이 이 행을 참조)
    private void saveDemoPayment(String paymentId, int amount, PaymentPurpose purpose, Long userId) {
        paymentRepository.save(Payment.builder()
            .paymentId(paymentId)
            .pgTxId("demo")
            .user(entityManager.getReference(User.class, userId))
            .amount(amount)
            .purpose(purpose)
            .build());
    }

    @Override
    public List<PaymentResponse.DetailDto> findMine(Long userId) {
        return real.findMine(userId);
    }

    @Override
    @Transactional
    public void refund(String paymentId, int amount, String reason) {
        if (paymentId == null || !paymentId.startsWith(DEMO_PREFIX)) {
            real.refund(paymentId, amount, reason);
            return;
        }
        // 가짜 결제는 PortOne 취소 없이 우리 DB 의 환불 누적액만 갱신한다
        Payment payment = paymentRepository.findByPaymentIdLock(paymentId)
            .orElseThrow(() -> new BadRequestException("결제 내역을 찾을 수 없습니다."));
        payment.refund(amount);
        log.info("[DEMO] 교환 보증금 환불 처리(PortOne 호출 없음) - paymentId={}, amount={}, reason={}", paymentId, amount, reason);
    }

    @Override
    @Transactional
    public void release(String paymentId) {
        real.release(paymentId);
    }
}
