package com.goodsbridge.payment;

import java.util.List;

/**
 * exchange(#18)/event(#19)/auction(#20)/purchase(#9)가 공통으로 호출하는 결제 검증/환불/지급 인터페이스입니다.
 * 메서드 이름·파라미터를 바꾸면 호출하는 쪽(exchange, event, auction, purchase, refund)도 같이 고쳐야 합니다.
 * 실제 구현은 {@link PaymentServiceImpl}에 있습니다.
 */

public interface PaymentService {

    /**
     * 클라이언트가 보낸 paymentId를 PortOne에 검증 요청하고, 승인 금액이 expectedAmount와 일치하는지 확인합니다.
     * 일치하지 않으면 BadRequestException(ErrorCode.INVALID_REQUEST)을 던집니다.
     *
     * @return 검증된 paymentId. 호출한 쪽(ExchangeRequest 등)이 본인 엔티티에 그대로 저장
     */
    String verify(String paymentId, int expectedAmount, PaymentPurpose purpose, Long userId);

    // 내 전체 결제 내역 (목적 불문)
    List<PaymentResponse.DetailDto> findMine(Long userId);

    /**
     * 이미 검증된 결제를 부분/전체 환불합니다 (PortOne 부분취소 API).
     */
    void refund(String paymentId, int amount, String reason);

    /** 구매자 수령 확인 이후 대금을 판매자에게 지급 처리합니다 (PAID -> RELEASED, 멱등). */
    void release(String paymentId);

    /**
     * 서버가 요청을 거절했는데 PortOne 에서는 이미 승인(PAID)된 결제를 전액 자동 취소합니다.
     * DB 에 기록된 결제는 건드리지 않으며, 취소에 실패해도 예외를 던지지 않고 로그만 남깁니다
     * (호출한 쪽의 원래 예외가 가려지지 않게 하기 위함).
     */
    default void cancelUnrecorded(String paymentId, String reason) {
    }
}
