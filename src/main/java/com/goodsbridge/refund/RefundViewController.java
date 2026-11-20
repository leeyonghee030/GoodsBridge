package com.goodsbridge.refund;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class RefundViewController {
    private final RefundService refundService;

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

    // 가게 주인의 환불 승인 (거절했던 건 재승인 포함)
    @PatchMapping("/api/shops/{shopId}/refunds/{id}/approve")
    public ResponseEntity<Void> approveByShop(@PathVariable Long shopId, @PathVariable Long id) {
        refundService.approveByShop(SecurityUtil.getCurrentUserId(), shopId, id);
        return ResponseEntity.ok().build();
    }

    // 가게 주인의 환불 거절 (이미 거절한 건은 거절 사유 수정)
    @PatchMapping("/api/shops/{shopId}/refunds/{id}/reject")
    public ResponseEntity<Void> rejectByShop(@PathVariable Long shopId, @PathVariable Long id, @RequestParam String rejectReason) {
        refundService.rejectByShop(SecurityUtil.getCurrentUserId(), shopId, id, rejectReason);
        return ResponseEntity.ok().build();
    }

    // 환불 내역 화면 호출 및 조회
    @GetMapping("/api/admin/refunds")
    public ResponseEntity<Page<RefundResponse.ListDto>> findAllAdmin(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size,
        @RequestParam(required = false) Status status) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(refundService.findAllAdmin(adminUserId, status, pageable));
    }

    // 가게 직접 환불 (하자/불량 등 가게 책임, 관리자 승인 없이 즉시 전체/부분 환불)
    @PostMapping("/api/shops/{shopId}/refunds")
    public ResponseEntity<RefundResponse.ListDto> refundByShop(@PathVariable Long shopId, @RequestBody ShopRefundRequest shopRefundRequest) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(refundService.refundByShop(userId, shopId, shopRefundRequest));
    }

    // 본인 환불 내역
    @GetMapping("/api/refunds/me")
    public ResponseEntity<List<RefundResponse.ListDto>> findMine() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(refundService.findMine(userId));
    }
}
