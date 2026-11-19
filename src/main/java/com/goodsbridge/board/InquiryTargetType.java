package com.goodsbridge.board;

/** 상품 문의가 어디에서 남겨졌는지 (상품·이벤트·경매 화면별로 자기 문의만 보여주기 위함) */
public enum InquiryTargetType {
    ITEM,     // 상품
    EVENT,    // 이벤트
    AUCTION   // 경매
}
