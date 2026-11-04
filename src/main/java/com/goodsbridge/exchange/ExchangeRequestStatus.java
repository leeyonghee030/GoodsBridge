package com.goodsbridge.exchange;

public enum ExchangeRequestStatus {
    PENDING("대기 중"),
    REJECTED("거절됨"),
    CANCELLED("취소됨"),
    PROPOSAL_EXPIRED("제안 만료"),
    CONFIRMED("확정됨"),
    COMPLETED("완료됨"),
    EXPIRED_PARTIAL("부분 만료 - 한쪽 노쇼"),
    EXPIRED_BOTH("양쪽 모두 만료 - 쌍방 노쇼");


    private final String displayName;

    private ExchangeRequestStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
