package com.goodsbridge.shop;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// 기존 ShopSubscriptionService 를 그대로 호출만 하는 구독 REST API (서비스 로직은 건드리지 않는다)
@RestController
@RequiredArgsConstructor
public class ShopSubscriptionController {

    private final ShopSubscriptionService shopSubscriptionService;

    // 가게 주인용 구독 시작/조회(POST·GET /api/shops/{shopId}/subscription)는 ShopViewController 에 있다.
    // 같은 주소를 여기에도 두면 요청 시 Ambiguous handler 로 500 이 나서 관리자용만 남긴다.

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
