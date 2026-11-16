package com.goodsbridge.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminResponse {

    // 처리 대기 현황 (월과 무관한 현재 값)
    private long pendingRefundCount;    // 환불 승인 대기
    private long pendingShipmentCount;  // 발송 대기
    private long activeAuctionCount;    // 진행 중 경매
    private long endedAuctionCount;     // 종료된 경매 (낙찰 성공·실패·취소, 누적)
    private long unpaidBalanceCount;    // 잔금 미납 낙찰

    // 최근 6개월 지표. 오래된 달부터 선택한 달 순서이고, 마지막 원소가 선택한 달이다.
    private List<MonthlyDto> months;

    @Getter
    @AllArgsConstructor
    public static class MonthlyDto {
        private String month;           // YYYY-MM
        private long signupCount;       // 월 가입자 수
        private long paymentCount;      // 월 거래 건수
        private long refundedAmount;    // 월 환불 금액
        private long settlementAmount;  // 월 정산 금액
        private long platformRevenue;   // 플랫폼 월 매출 (구독료)
        private long shopRevenue;       // 가게 월 매출
    }
}
