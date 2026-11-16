package com.goodsbridge.exchange.dto;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

// 가게 교환 관리 상단 요약: 오늘 방문 예정 + 교환 수수료 수익 + 이번 달 수수료 내역
@Getter
public class ShopExchangeSummaryResponse {

    private final List<ShopExchangeResponse> todayItems; // 오늘 만나기로 확정된 교환 (약속 시간 순)
    private final long feeToday;                         // 오늘 정산된 교환 수수료(가게 몫)
    private final long feeMonth;                         // 이번 달
    private final long feeLastMonth;                     // 저번 달
    private final List<FeeRecord> feeRecords;            // 이번 달 정산 내역 (교환 1건당 1줄, 최신순)

    public ShopExchangeSummaryResponse(List<ShopExchangeResponse> todayItems, long feeToday, long feeMonth, long feeLastMonth,
                                       List<FeeRecord> feeRecords) {
        this.todayItems = todayItems;
        this.feeToday = feeToday;
        this.feeMonth = feeMonth;
        this.feeLastMonth = feeLastMonth;
        this.feeRecords = feeRecords;
    }

    // 교환 1건의 수수료 정산 내역
    @Getter
    public static class FeeRecord {
        private final Long exchangeId;
        private final LocalDateTime settledAt;
        private final String kind;        // COMPLETED(정상 교환 수수료) / NO_SHOW(불참 차감)
        private final long amount;        // 이 교환에서 가게가 가져간 합계
        private final String userANickname;
        private final String userBNickname;
        private final List<Part> parts;   // 사람별 차감액

        public FeeRecord(Long exchangeId, LocalDateTime settledAt, String kind, long amount,
                         String userANickname, String userBNickname, List<Part> parts) {
            this.exchangeId = exchangeId;
            this.settledAt = settledAt;
            this.kind = kind;
            this.amount = amount;
            this.userANickname = userANickname;
            this.userBNickname = userBNickname;
            this.parts = parts;
        }
    }

    @Getter
    public static class Part {
        private final String nickname;
        private final long amount;

        public Part(String nickname, long amount) {
            this.nickname = nickname;
            this.amount = amount;
        }
    }
}
