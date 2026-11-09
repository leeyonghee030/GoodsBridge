package com.goodsbridge.exchange;

public enum ExchangeItemStatus {
    OPEN("모집중"),
    MATCHED("매칭완료"),
    CLOSED("마감");

    private final String displayName;

    private ExchangeItemStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
