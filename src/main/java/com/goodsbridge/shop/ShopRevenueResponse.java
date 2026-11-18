package com.goodsbridge.shop;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

// 가게 대시보드 '매출' 창: 고른 해의 월별 매출과 종류별 내역
@Getter
@AllArgsConstructor
public class ShopRevenueResponse {

    private int year;                   // 조회한 해
    private long totalAmount;           // 그 해 합계
    private List<MonthDto> months;      // 1월부터 (올해는 이번 달까지)

    @Getter
    @AllArgsConstructor
    public static class MonthDto {
        private String month;           // YYYY-MM
        private long totalAmount;       // 월 매출 (아래 종류별 합계)
        private long purchaseAmount;    // 일반 판매 대금
        private long exchangeAmount;    // 교환 수수료
        private long noShowAmount;      // 노쇼 몰수금
        private long auctionAmount;     // 경매 낙찰가
        private long eventAmount;       // 이벤트 참가비
    }
}
