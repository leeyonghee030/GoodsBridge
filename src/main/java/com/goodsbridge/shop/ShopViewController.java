package com.goodsbridge.shop;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ShopViewController {

    private final ShopService shopService;
    private final ShopSubscriptionService shopSubscriptionService;

    @PostMapping("/api/shops")
    public ResponseEntity<ShopResponse> createShop(@RequestBody ShopCreateRequest requestDto) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(shopService.create(userId, requestDto));
    }

    @GetMapping("/api/shops")
    public ResponseEntity<Page<ShopResponse>> getShops(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String region,
        @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(shopService.findAll(name, region, pageable));
    }

    @GetMapping("/api/shops/me")
    public ResponseEntity<Page<ShopResponse>> getMyShops(
        @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopService.findAllByOwnerUserId(userId, pageable));
    }

    @GetMapping("/api/shops/{id}")
    public ResponseEntity<ShopResponse> getShop(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.findById(id));
    }

    @PatchMapping("/api/shops/{id}")
    public ResponseEntity<ShopResponse> updateShop(
        @PathVariable Long id,
        @RequestBody ShopUpdateRequest requestDto) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopService.update(userId, id, requestDto));
    }

    @DeleteMapping("/api/shops/{id}")
    public ResponseEntity<Void> deleteShop(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        shopService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    // --- 관리자 전용 API ---

    @GetMapping("/api/admin/shops/search")
    public ResponseEntity<Page<ShopResponse>> searchShopsByAdmin(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String region,
        @RequestParam(required = false) ShopStatus status,
        @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {

        Long adminUserId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopService.searchShopsByAdmin(adminUserId, name, region, status, pageable));
    }

    // 관리자 입점 승인
    @PatchMapping("/api/admin/shops/{id}/approve")
    public ResponseEntity<Void> approveShop(@PathVariable Long id) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        shopService.approve(adminUserId, id);
        return ResponseEntity.ok().build();
    }

    // 관리자 입점 거절
    @PatchMapping("/api/admin/shops/{id}/reject")
    public ResponseEntity<Void> rejectShop(@PathVariable Long id) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        shopService.reject(adminUserId, id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/api/admin/shops/{id}/close")
    public ResponseEntity<Void> closeShop(@PathVariable Long id) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        shopService.close(adminUserId, id);
        return ResponseEntity.ok().build();
    }

    // --- 관리자 전용 스케줄러 수동 트리거 API ---

    @PostMapping("/api/admin/scheduler/subscriptions")
    public ResponseEntity<SchedulerResponse> triggerExpiredSubscriptionBatch() {
        log.info("[관리자 API] 수동 스케줄러 트리거 요청 수신: 만료 구독 일괄 처리");

        int processedCount = shopSubscriptionService.processExpiredSubscriptionsByAdmin();

        log.info("[관리자 API] 수동 스케줄러 실행 완료: 총 {}건 처리됨", processedCount);

        return ResponseEntity.ok(new SchedulerResponse(true, processedCount, "만료 구독 배치가 성공적으로 수동 실행되었습니다."));
    }


    public record SchedulerResponse(
        boolean success,
        int processedCount,
        String message
    ) {}
}
