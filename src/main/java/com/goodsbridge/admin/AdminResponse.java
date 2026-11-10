package com.goodsbridge.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
public class AdminResponse {
    // 기존 통계
    private long userCount;   // 유저 수
    private long paymentCount;  // 결제 수
    private long paymentAmount; // 결제 금액
    private long revenue;   // 수익

    // 월별 통계
    private long refundedAmount;     // 환불액 합계
    private long settlementAmount;   // 가게 정산 금액 합계
    private Map<String, Long> paymentAmountByPurpose;   // 결제 목적별 금액

    // 현재 처리 대기 현황 (월과 무관)
    private long pendingRefundCount;    // 승인 대기 환불 요청
    private long activeAuctionCount;   // 진행 중 경매
    private long unpaidBalanceCount;     // 잔금 미납 낙찰 경매
    private long pendingShipmentCount;    // 발송 대기 구매
}
