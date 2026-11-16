package com.goodsbridge.admin;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AdminResponse {

    // 현재 값 (월과 무관)
    private long pendingShopCount;        // 입점 신청 대기
    private long approvedShopCount;       // 입점 승인된 가게
    private long activeSubscriptionCount; // 구독 중인 가게
    private long expiringSoonCount;       // 7일 안에 구독이 끝나는 가게
    private long subscriptionRatePercent; // 구독 전환율(%): 승인된 가게 중 구독 중인 가게 비율

    // 최근 6개월 지표. 오래된 달부터 선택한 달 순서이고, 마지막 원소가 선택한 달이다.
    private List<MonthlyDto> months;

    @Getter
    @AllArgsConstructor
    public static class MonthlyDto {
        private String month;               // YYYY-MM
        private long signupCount;           // 월 가입자 수
        private long subscriptionCount;     // 월 구독 결제 건수
        private long subscriptionRevenue;   // 플랫폼 월 매출 (구독료)
        private long newShopCount;          // 월 신규 입점 가게 수
        private long churnCount;            // 월 구독 이탈 가게 수 (만료·취소)
    }
}
