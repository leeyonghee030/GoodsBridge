package com.goodsbridge.settlement;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    // 가게 정산 내역 조회 (type, from, to 는 선택 조건, 페이지 단위)
    @GetMapping("/api/shops/{id}/settlements")
    public ResponseEntity<Page<SettlementHistoryResponse.ListDto>> shopSettlements(
        @PathVariable Long id,
        @RequestParam(required = false) SettlementHistoryType type,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
            Sort.by(Sort.Direction.DESC, "id"));
        return ResponseEntity.ok(settlementHistoryService.findByShop(id, userId, type, from, to, pageable));
    }

    // 가게 매출 요약 (총매출 / 구독료 지출 / 순매출 + 유형별 합계)
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
