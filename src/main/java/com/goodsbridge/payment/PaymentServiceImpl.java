package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * PortOne 결제 검증(#9), 부분취소(#17), 대금 지급(release) 구현체.
 * 금액 검증의 기준은 항상 호출하는 쪽(서버)이 넘기는 expectedAmount이며 클라이언트 값은 신뢰하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PortOnePaymentService portOnePaymentService;
    private final EntityManager entityManager;

    // 결제
    @Override
    @Transactional
    public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) {
        // 결제 Id 조회
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("paymentId가 필요합니다.");
        }

        // 예상 금액, 목적, 사용자 Id 조회
        if (expectedAmount <= 0 || purpose == null || userId == null) {
            throw new BadRequestException("결제 검증에 필요한 값이 올바르지 않습니다.");
        }

        // 이미 처리된 결제인지 확인
        if (paymentRepository.existsByPaymentId(paymentId)) {
            throw new BadRequestException("이미 처리된 결제입니다.");
        }

        // PortOne 결제 조회
        PaymentResponse.PortOnePayment portOnePayment =
            portOnePaymentService.getPayment(paymentId);
        if (portOnePayment == null) {
            throw new BadRequestException("PortOne에서 결제 내역을 찾을 수 없습니다.");
        }

        // 결제 상태 확인
        if (!"PAID".equals(portOnePayment.getStatus())) {
            throw new BadRequestException("결제가 승인된 상태가 아닙니다.");
        }

        // 금액 위 변조 확인
        if (portOnePayment.getAmount() == null
            || portOnePayment.getAmount().getTotal() == null
            || !portOnePayment.getAmount().getTotal().equals(expectedAmount)) {
            throw new BadRequestException("결제 금액이 일치하지 않습니다.");
        }

        // 검증 통과 + 결제 기록
        User user = entityManager.getReference(User.class, userId);
        Payment payment = Payment.builder()
            .pgTxId(portOnePayment.getPgTxId())
            .paymentId(paymentId)
            .user(user)
            .amount(expectedAmount)
            .purpose(purpose)
            .build();

        log.info("결제 저장 - paymentId={}, userId={}, amount={}, purpose={}",
            paymentId, userId, expectedAmount, purpose);

        try {
            paymentRepository.save(payment);
        } catch (DataIntegrityViolationException e) {
            // existsByPaymentId 검사를 동시에 통과한 중복 요청 -> payment_id unique 제약이 마지막 방어선
            throw new BadRequestException("이미 처리된 결제입니다.");
        }
        return paymentId;
    }

    // 내 전체 결제 내역
    @Override
    public List<PaymentResponse.DetailDto> findMine(Long userId) {
        return paymentRepository.findAllByUserIdOrderByIdDesc(userId).stream()
            .map(PaymentResponse.DetailDto::new).toList();
    }

    // 환불
    @Override
    @Transactional
    public void refund(String paymentId, int amount, String reason) {
        if (paymentId == null || paymentId.isBlank() || amount <= 0) {
            throw new BadRequestException("환불 요청 값이 올바르지 않습니다.");
        }
        // 같은 결제를 동시에 환불/지급하지 못하도록 행 잠금 후 한도를 다시 검증한다.
        Payment payment = paymentRepository.findByPaymentIdLock(paymentId)
            .orElseThrow(() -> new BadRequestException("결제 내역을 찾을 수 없습니다."));
        if (!payment.isPaid() && payment.getStatus() != PaymentStatus.PARTIAL_CANCELLED) {
            throw new BadRequestException("환불 가능한 결제 상태가 아닙니다.");
        }
        if (amount > payment.getRefundableAmount()) {
            throw new BadRequestException("환불 가능 금액을 초과했습니다.");
        }
        PaymentResponse.PortOneCancel result =
            portOnePaymentService.cancelPayment(paymentId, amount, reason);
        if (result == null || result.getCancellation() == null
            || !"SUCCEEDED".equals(result.getCancellation().getStatus())) {
            throw new BadRequestException("결제 취소가 완료되지 않았습니다.");
        }
        payment.refund(amount);
    }

    // 수령 확인 후 판매자에게 지급 처리
    @Override
    @Transactional
    public void release(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException("paymentId가 필요합니다.");
        }
        Payment payment = paymentRepository.findByPaymentIdLock(paymentId)
            .orElseThrow(() -> new BadRequestException("결제 내역을 찾을 수 없습니다."));
        payment.release();
    }

    // 서버 거절로 구매가 실패했을 때 PortOne 에 남은 승인 결제를 자동 취소
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void cancelUnrecorded(String paymentId, String reason) {
        if (paymentId == null || paymentId.isBlank()) {
            return;
        }
        try {
            // 이미 정상 처리(기록)된 결제는 절대 취소하지 않는다. 새 트랜잭션이라 커밋된 결과만 보인다.
            if (paymentRepository.existsByPaymentId(paymentId)) {
                return;
            }
            PaymentResponse.PortOnePayment portOnePayment = portOnePaymentService.getPayment(paymentId);
            if (portOnePayment == null || !"PAID".equals(portOnePayment.getStatus())
                || portOnePayment.getAmount() == null
                || portOnePayment.getAmount().getTotal() == null
                || portOnePayment.getAmount().getTotal() <= 0) {
                return;
            }
            PaymentResponse.PortOneCancel result = portOnePaymentService
                .cancelPayment(paymentId, portOnePayment.getAmount().getTotal(), reason);
            if (result == null || result.getCancellation() == null
                || !"SUCCEEDED".equals(result.getCancellation().getStatus())) {
                log.error("자동 취소 미완료 - 수동 취소 필요 paymentId={}", paymentId);
                return;
            }
            log.info("서버 거절로 결제 자동 취소 완료 - paymentId={}, amount={}",
                paymentId, portOnePayment.getAmount().getTotal());
        } catch (RuntimeException e) {
            log.error("자동 취소 실패 - 수동 취소 필요 paymentId={}", paymentId, e);
        }
    }
}
