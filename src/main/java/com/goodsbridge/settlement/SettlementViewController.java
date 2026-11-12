package com.goodsbridge.settlement;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class SettlementViewController {
    private final SettlementHistoryService settlementHistoryService;

    // 가게 정산 내역 조회 (type, from, to 는 선택 조건)
    @GetMapping("/api/shops/{id}/settlements")
    public ResponseEntity<List<SettlementHistoryResponse.ListDto>> shopSettlements(
        @PathVariable Long id,
        @RequestParam(required = false) SettlementHistoryType type,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(settlementHistoryService.findByShop(id, userId, type, from, to));
    }

    // 가게 매출 요약 (정산 총액 / 플랫폼 수수료 / 실수령액 + 유형별 합계)
    @GetMapping("/api/shops/{id}/settlements/summary")
    public ResponseEntity<SettlementHistoryResponse.SummaryDto> shopSettlementSummary(
        @PathVariable Long id,
        @RequestParam(required = false) SettlementHistoryType type,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(settlementHistoryService.summarizeByShop(id, userId, type, from, to));
    }
}
