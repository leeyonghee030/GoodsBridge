package com.goodsbridge.settlement;

public enum SettlementHistoryType {
    PURCHASE_FEE,
    EXCHANGE_FEE,   // 교환 금액 수수료
    AUCTION_FEE, // 경매 금액 수수료
    EVENT_FEE,   // 이벤트 금액 수수료
    NO_SHOW_FEE // 노쇼 시 금액
}
