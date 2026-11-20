package com.goodsbridge.demo;

import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentResponse;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.UserRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;

// 테스트에서는 데모 모드가 꺼져 있어 DemoPaymentService 가 없으므로, PortOne 없이 결제 행만 저장·환불하는 가짜 결제를 쓴다
// (정산·환불 코드가 결제 행을 찾으므로 행은 실제로 저장한다)
@TestConfiguration
class DemoFakePaymentConfig {

    @Bean
    @Primary
    PaymentService fakePaymentService(PaymentRepository paymentRepository, UserRepository userRepository) {
        return new PaymentService() {
            @Override
            public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) {
                if (paymentRepository.findByPaymentId(paymentId).isEmpty()) {
                    paymentRepository.save(Payment.builder().paymentId(paymentId).pgTxId("test")
                        .user(userRepository.getReferenceById(userId)).amount(expectedAmount).purpose(purpose).build());
                }
                return paymentId;
            }

            @Override
            public List<PaymentResponse.DetailDto> findMine(Long userId) {
                return List.of();
            }

            @Override
            public void refund(String paymentId, int amount, String reason) {
                paymentRepository.findByPaymentId(paymentId).ifPresent(p -> {
                    p.refund(amount);
                    paymentRepository.save(p);
                });
            }

            @Override
            public void release(String paymentId) {
            }
        };
    }
}
