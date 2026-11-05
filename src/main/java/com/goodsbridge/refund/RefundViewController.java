package com.goodsbridge.refund;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.settlement.SettlementHistoryResponse;
import com.goodsbridge.settlement.SettlementHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class RefundViewController {
    private final RefundService refundService;
    private final SettlementHistoryService settlementHistoryService;

    @GetMapping("/test/refund")

    public String index() {
        return "refund/index";
    }

    // 환불 요청
    @PostMapping("/api/refunds")
    public ResponseEntity<RefundResponse.ListDto> requestRefund(@RequestBody RefundRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(refundService.request(userId, request));
    }

    // 관리자 환불 승인(부분/전체 취소)
    @PatchMapping("/api/admin/refunds/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long id) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        refundService.approve(id, adminUserId);
        return ResponseEntity.ok().build();
    }

    // 관리자 환불 거절
    @PatchMapping("/api/admin/refunds/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable Long id, @RequestParam String rejectReason) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        refundService.reject(adminUserId, id, rejectReason);
        return ResponseEntity.ok().build();
    }

    // 가게 정산 / 매출 내역 조회
    @GetMapping("/api/shops/{id}/settlements")
    public ResponseEntity<List<SettlementHistoryResponse.ListDto>> shopSettlements(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(settlementHistoryService.findByShop(id, userId));
    }

    // 환불 내역 화면 호출 및 조회
    @GetMapping("/api/admin/refunds")
    public ResponseEntity<List<RefundResponse.ListDto>> findAllAdmin() {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(refundService.findAllAdmin(adminUserId));
    }

    // 가게 환불 신청
    @PostMapping("/api/shops/{shopId}/refunds")
    public ResponseEntity<RefundResponse.ListDto> requestShopRefund(@PathVariable Long shopId, @RequestBody ShopRefundRequest shopRefundRequest) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(refundService.requestedByShop(userId, shopId, shopRefundRequest));
    }

    // 본인 환불 내역
    @GetMapping("/api/refunds/me")
    public ResponseEntity<List<RefundResponse.ListDto>> findMine() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(refundService.findMine(userId));
    }
}
