package com.goodsbridge.payment;

public enum PaymentStatus {
    READY,  // 준비
    PAID,   // 완료
    PARTIAL_CANCELLED,  // 부분 취소
    CANCELLED   // 전체 취소
}
