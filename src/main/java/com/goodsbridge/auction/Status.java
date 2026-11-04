package com.goodsbridge.auction;

public enum Status {
    ACTIVE,          // 경매 중
    ENDED_SUCCESS,  // 낙찰 성공
    ENDED_FAIL,     // 낙찰 실패
    CANCELLED       // 경매 취소
}
