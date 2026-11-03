package com.goodsbridge.payment;

import java.util.List;

/**
 * exchange(#18)/event(#19)/auction(#20)이 공통으로 호출하는 결제 검증/환불 인터페이스 초안입니다.
 * 메서드 이름·파라미터는 C/D가 상의해서 확정하고, 바뀌면 이 파일을 같이 고쳐주세요.
 * 실제 구현(#9, #17)이 끝나기 전까지는 호출하면 예외가 던져집니다.
 */

public interface PaymentService {

    /**
     * 클라이언트가 보낸 paymentId를 PortOne에 검증 요청하고, 승인 금액이 expectedAmount와 일치하는지 확인합니다.
     * 일치하지 않으면 BadRequestException(ErrorCode.INVALID_REQUEST)을 던집니다.
     *
     * @return 검증된 paymentId. 호출한 쪽(ExchangeRequest 등)이 본인 엔티티에 그대로 저장
     */
    String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId);

    // 내 구매 목록
    List<PaymentResponse.DetailDto> findMine(Long userId);

    /**
     * 이미 검증된 결제를 부분/전체 환불합니다 (PortOne 부분취소 API).
     */
    void refund(String paymentId, int amount, String reason);
}
