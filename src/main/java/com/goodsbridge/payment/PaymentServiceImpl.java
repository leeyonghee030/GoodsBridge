package com.goodsbridge.payment;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * #9(결제/구매), #17(정산/환불)에서 실제 로직을 채웁니다.
 * 지금은 다른 도메인이 컴파일/개발을 먼저 시작할 수 있게 자리만 잡아둔 상태입니다.
 */
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository paymentRepository;
    private final PortOneClient portOneClient;
    private final EntityManager entityManager;

    // 결제
    @Override
    @Transactional
    public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) {
        // 결제 Id 조회
        if (paymentId == null || paymentId.isBlank()) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // 예상 금액, 목적, 사용자 Id 조회
        if (expectedAmount <= 0 || purpose == null || userId == null) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // 이미 처리된 결제인지 확인
        if (paymentRepository.existsByPaymentId(paymentId)) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // PortOne 결제 조회
        PaymentResponse.PortOnePayment portOnePayment =
            portOneClient.getPayment(paymentId);
        if (portOnePayment == null) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // 결제 상태 확인
        if (!"PAID".equals(portOnePayment.getStatus())) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // 금액 위 변조 확인
        if (portOnePayment.getAmount() == null
            || portOnePayment.getAmount().getTotal() == null
            || !portOnePayment.getAmount().getTotal().equals(expectedAmount)) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
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
        paymentRepository.save(payment);
        return paymentId;
    }

    // 내 구매 목록
    @Override
    public List<PaymentResponse.DetailDto> findMine(Long userId) {
        return paymentRepository.findAllByUserIdOrderByIdDesc(userId).stream()
            .map(PaymentResponse.DetailDto::new).toList();
    }

    // 환불
    @Override
    public void refund(String paymentId, int amount, String reason) {
        // #17에서 구현
        throw new UnsupportedOperationException(
            "환불은 이슈 #17에서 구현합니다."
        );
    }
}
