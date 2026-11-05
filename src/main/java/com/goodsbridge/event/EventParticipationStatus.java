package com.goodsbridge.event;


public enum EventParticipationStatus {

    JOINED("참여"),
    CANCELLED("취소"),
    REFUNDED("환불");

    private final String displayName;

    private EventParticipationStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
