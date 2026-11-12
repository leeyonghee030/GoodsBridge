package com.goodsbridge.shop;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 기존 ShopSubscriptionService 를 그대로 호출만 하는 구독 REST API (서비스 로직은 건드리지 않는다)
@RestController
@RequiredArgsConstructor
public class ShopSubscriptionController {

    private final ShopSubscriptionService shopSubscriptionService;

    // 구독 시작/갱신 (가게 주인)
    @PostMapping("/api/shops/{id}/subscription")
    public ResponseEntity<Long> subscribe(@PathVariable Long id, @RequestBody ShopSubscriptionRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(shopSubscriptionService.create(userId, id, request));
    }

    // 내 가게 구독 현황 (가게 주인)
    @GetMapping("/api/shops/{id}/subscription")
    public ResponseEntity<ShopSubscriptionResponse> mySubscription(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopSubscriptionService.findById(userId, id));
    }

    // 구독 취소 (관리자)
    @PatchMapping("/api/admin/subscriptions/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        shopSubscriptionService.cancelByAdmin(adminUserId, id);
        return ResponseEntity.ok().build();
    }

    // 전체 가게 구독 현황 (관리자)
    @GetMapping("/api/admin/subscriptions")
    public ResponseEntity<List<ShopSubscriptionResponse>> allSubscriptions() {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopSubscriptionService.findAllSubscriptions(adminUserId));
    }
}
