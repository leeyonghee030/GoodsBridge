package com.goodsbridge.exchange.dto;

import lombok.Getter;

import java.util.List;

// 가게 교환 관리 목록: 탭별 전체 건수 + (최신 100건 이내) 검색된 교환
@Getter
public class ShopExchangeListResponse {

    public static final int LIMIT = 100;

    private final long waitingCount;
    private final long doneCount;
    private final List<ShopExchangeResponse> items;
    private final boolean limited; // 조건에 맞는 교환이 LIMIT 보다 많아 최신 LIMIT 건만 내려준 경우

    public ShopExchangeListResponse(long waitingCount, long doneCount, List<ShopExchangeResponse> items, boolean limited) {
        this.waitingCount = waitingCount;
        this.doneCount = doneCount;
        this.items = items;
        this.limited = limited;
    }
}
