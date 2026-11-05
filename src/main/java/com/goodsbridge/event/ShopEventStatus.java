package com.goodsbridge.event;

public enum ShopEventStatus {
    OPEN("모집중"),
    CLOSED("마감"),
    CANCELLED("취소");

    private final String displayName;

    private ShopEventStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
