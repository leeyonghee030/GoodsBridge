package com.goodsbridge.payment;

import org.springframework.stereotype.Service;

/**
 * #9(결제/구매), #17(정산/환불)에서 실제 로직을 채웁니다.
 * 지금은 다른 도메인이 컴파일/개발을 먼저 시작할 수 있게 자리만 잡아둔 상태입니다.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    @Override
    public String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId) {
        throw new UnsupportedOperationException("PaymentService.verify()는 #9 완료 후 사용 가능합니다");
    }

    @Override
    public void refund(String paymentId, int amount, String reason) {
        throw new UnsupportedOperationException("PaymentService.refund()는 #17 완료 후 사용 가능합니다");
    }
}
