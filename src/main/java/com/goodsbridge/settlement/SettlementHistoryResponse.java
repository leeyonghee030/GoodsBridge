package com.goodsbridge.settlement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class SettlementHistoryResponse {
    @Data
    public static class ListDto {
        private Long id;
        private Long shopId;
        private String paymentId;
        private Integer amount;        // 가게가 받은 총액
        private Integer platformFee;   // 플랫폼 수수료
        private Integer netAmount;     // 가게 실수령액 (총액 - 수수료)
        private SettlementHistoryType type;
        private LocalDateTime createdAt;

        public ListDto(SettlementHistory settlementHistory) {
            this.id = settlementHistory.getId();
            this.shopId = settlementHistory.getShop().getId();
            this.paymentId = settlementHistory.getPayment().getPaymentId();
            this.amount = settlementHistory.getAmount();
            this.platformFee = settlementHistory.getPlatformFeeOrZero();
            this.netAmount = settlementHistory.getNetAmount();
            this.type = settlementHistory.getType();
            this.createdAt = settlementHistory.getCreatedAt();
        }
    }

    // 가게 정산/매출 요약 (조회 조건에 해당하는 정산 전체의 합계 + 유형별 합계)
    @Data
    public static class SummaryDto {
        private int count;                 // 정산 건수
        private long totalAmount;          // 가게가 받은 총액
        private long totalPlatformFee;     // 플랫폼 수수료 합계
        private long totalNetAmount;       // 가게 실수령액 합계 (총액 - 수수료)
        private List<TypeSummaryDto> byType;

        public SummaryDto(List<SettlementHistory> histories) {
            this.count = histories.size();
            for (SettlementHistory history : histories) {
                this.totalAmount += history.getAmount() == null ? 0 : history.getAmount();
                this.totalPlatformFee += history.getPlatformFeeOrZero();
                this.totalNetAmount += history.getNetAmount();
            }
            // 유형별 합계 (enum 선언 순서대로)
            Map<SettlementHistoryType, List<SettlementHistory>> grouped = new EnumMap<>(SettlementHistoryType.class);
            for (SettlementHistory history : histories) {
                grouped.computeIfAbsent(history.getType(), key -> new ArrayList<>()).add(history);
            }
            this.byType = grouped.entrySet().stream()
                .map(entry -> new TypeSummaryDto(entry.getKey(), entry.getValue()))
                .toList();
        }
    }

    @Data
    public static class TypeSummaryDto {
        private SettlementHistoryType type;
        private int count;
        private long totalAmount;
        private long totalPlatformFee;
        private long totalNetAmount;

        public TypeSummaryDto(SettlementHistoryType type, List<SettlementHistory> histories) {
            this.type = type;
            this.count = histories.size();
            for (SettlementHistory history : histories) {
                this.totalAmount += history.getAmount() == null ? 0 : history.getAmount();
                this.totalPlatformFee += history.getPlatformFeeOrZero();
                this.totalNetAmount += history.getNetAmount();
            }
        }
    }
}
