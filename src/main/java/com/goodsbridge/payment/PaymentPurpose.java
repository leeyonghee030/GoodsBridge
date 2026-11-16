package com.goodsbridge.payment;

public enum PaymentPurpose {
    PURCHASE,   // 구매
    EXCHANGE_DEPOSIT,      // 교환 전 예치금
    EVENT_FEE,  // 이벤트 금액
    AUCTION_DEPOSIT, // 경매장 예치금
    AUCTION_BALANCE, // 경매 낙찰 잔금 (낙찰가 - 보증금)
    SHOP_SUBSCRIPTION // 가게 B2B SaaS 구독 결제 (추가)
}
