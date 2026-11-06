package com.goodsbridge.shop;

import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.payment.PaymentResponse;

import java.util.Collections;
import java.util.List;

public class FakePaymentService implements PaymentService {

    @Override
    public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) {
        // 동시성 테스트 등에서 결제 검증이 항상 성공한 것처럼 paymentId를 그대로 반환
        return paymentId;
    }

    @Override
    public List<PaymentResponse.DetailDto> findMine(Long userId) {
        return Collections.emptyList();
    }

    @Override
    public void refund(String paymentId, int amount, String reason) {
        // No-op (테스트용이므로 아무 작업도 하지 않음)
    }

    @Override
    public void release(String paymentId) {
        // No-op
    }
}
