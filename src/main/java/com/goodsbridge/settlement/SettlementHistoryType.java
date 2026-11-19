package com.goodsbridge.settlement;

public enum SettlementHistoryType {
    PURCHASE_FEE,   // 구매 금액
    EXCHANGE_FEE,   // 교환 금액
    AUCTION_SALE, // 경매 낙찰가 전액 정산
    EVENT_FEE,   // 이벤트 금액
    NO_SHOW_FEE, // 노쇼 시 금액
    AUCTION_DEPOSIT_FORFEIT // 경매 보증금
}
