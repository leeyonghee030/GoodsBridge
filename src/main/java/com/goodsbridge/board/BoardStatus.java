package com.goodsbridge.board;

public enum BoardStatus {
    PENDING("답변 대기"),
    COMPLETED("답변 완료");

    private final String description;

    BoardStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

