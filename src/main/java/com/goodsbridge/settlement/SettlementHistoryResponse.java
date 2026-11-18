package com.goodsbridge.settlement;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SettlementHistoryResponse {
    @Data
    public static class ListDto {
        private Long id;
        private Long shopId;
        private String paymentId;
        private Integer amount;        // 가게가 받은 총액
        private SettlementHistoryType type;
        private LocalDateTime createdAt;

        public ListDto(SettlementHistory settlementHistory) {
            this.id = settlementHistory.getId();
            this.shopId = settlementHistory.getShop().getId();
            this.paymentId = settlementHistory.getPayment().getPaymentId();
            this.amount = settlementHistory.getAmount();
            this.type = settlementHistory.getType();
            this.createdAt = settlementHistory.getCreatedAt();
        }
    }

    // 가게 매출 요약 (총매출, 구독료 지출, 순매출 + 유형별 합계)
    @Data
    public static class SummaryDto {
        private int count;                  // 정산 건수
        private long grossRevenue;          // 총매출 (정산 합계)
        private long subscriptionExpense;   // 구독료 지출
        private long netRevenue;            // 순매출 (총매출 - 구독료 지출)
        private List<TypeSummaryDto> byType;

        // 유형별과 구독료 지출로 요약을 만든다
        public static SummaryDto of(List<Object[]> rows, long subscriptionExpense) {
            SummaryDto dto = new SummaryDto();
            List<TypeSummaryDto> byType = new ArrayList<>();
            for (Object[] row : rows) {
                TypeSummaryDto type = new TypeSummaryDto(
                    (SettlementHistoryType) row[0],
                    ((Number) row[1]).intValue(),
                    ((Number) row[2]).longValue());
                byType.add(type);
                dto.count += type.getCount();
                dto.grossRevenue += type.getTotalAmount();
            }
            byType.sort((a, b) -> a.getType().compareTo(b.getType()));   // enum 선언 순서
            dto.byType = byType;
            dto.subscriptionExpense = subscriptionExpense;
            dto.netRevenue = dto.grossRevenue - subscriptionExpense;
            return dto;
        }
    }

    @Data
    public static class TypeSummaryDto {
        private SettlementHistoryType type;
        private int count;
        private long totalAmount;

        public TypeSummaryDto(SettlementHistoryType type, int count, long totalAmount) {
            this.type = type;
            this.count = count;
            this.totalAmount = totalAmount;
        }
    }
}
