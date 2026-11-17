package com.goodsbridge.shop;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDate;
import java.util.List;

@Getter
@AllArgsConstructor
public class ShopDashboardResponse {

    // 처리할 일 (월과 무관한 현재 값)
    private long pendingRefundCount;    // 환불 승인 대기
    private long pendingShipmentCount;  // 발송 대기
    private long unpaidBalanceCount;    // 잔금 미납 낙찰
    private long activeAuctionCount;    // 진행 중 경매
    private long endedAuctionCount;     // 종료된 경매 (낙찰·유찰, 누적. 취소는 제외)
    private long todayMeetingCount;     // 오늘 방문 예정 교환
    private long waitingExchangeCount;  // 확정되어 방문·출석 체크를 기다리는 교환
    private long openEventCount;        // 모집 중인 이벤트
    private long pendingSettlementAmount;   // 정산 예정액: 낙찰됐지만 잔금 결제를 기다리는 경매의 낙찰가 합계

    // 구독 (승인된 가게 기준). 구독 중인 가게가 하나도 없으면 날짜는 null
    private LocalDate subscriptionEndDate;  // 가장 먼저 끝나는 구독의 종료일
    private Integer subscriptionDaysLeft;   // 그 종료일까지 남은 일수
    private long inactiveShopCount;         // 승인됐지만 구독 중이 아닌 가게 수

    // 최근 6개월 지표. 오래된 달부터 선택한 달 순서이고, 마지막 원소가 선택한 달이다.
    private List<MonthlyDto> months;

    @Getter
    @AllArgsConstructor
    public static class MonthlyDto {
        private String month;           // YYYY-MM
        private long saleCount;         // 월 판매 건수
        private long revenue;           // 월 매출
        private long refundedAmount;    // 월 환불 금액
    }
}
