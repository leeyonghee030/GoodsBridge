package com.goodsbridge.refund;

public enum RefundType {
    PURCHASE,
    EXCHANGE_CANCEL,    // 교환 취소
    EXCHANGE_REJECT,    // 교환 제안 거절
    EXCHANGE_EXPIRED,   // 응답 기한 초과
    NO_SHOW;

    // 교환 수수료를 받지 않는 환불인지 (교환 제안 거절 / 교환 취소 / 응답 기한 초과)
    // 이 유형은 환불만 처리하고 settlementHistory 를 만들지 않는다.
    public boolean isFeeFree() {
        return this == EXCHANGE_CANCEL || this == EXCHANGE_REJECT || this == EXCHANGE_EXPIRED;
    }
}
