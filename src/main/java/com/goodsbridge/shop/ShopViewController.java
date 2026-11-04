package com.goodsbridge.shop;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ShopViewController {

    private final ShopService shopService;

    // 입점 신청
    @PostMapping("api/shops")
    public ResponseEntity<ShopResponse> createShop(@RequestBody ShopCreateRequest requestDto) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(shopService.create(userId, requestDto));
    }

    // 디렉터리 조회
    @GetMapping("/api/shops")
    public ResponseEntity<List<ShopResponse>> getShops(
        @RequestParam(required = false) String name,
        @RequestParam(required = false) String region) {
        return ResponseEntity.ok(shopService.findAll(name, region));
    }

    // 내 가게 목록 조회
    @GetMapping("/api/shops/me")
    public ResponseEntity<List<ShopResponse>> getMyShops() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopService.findAllByOwnerUserId(userId));
    }

    // 가게 단건 상세 조회
    @GetMapping("/api/shops/{id}")
    public ResponseEntity<ShopResponse> getShop(@PathVariable Long id) {
        return ResponseEntity.ok(shopService.findById(id));
    }

    // 가게 수정
    @PatchMapping("/api/shops/{id}")
    public ResponseEntity<ShopResponse> updateShop(
        @PathVariable Long id,
        @RequestBody ShopUpdateRequest requestDto) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopService.update(userId, id, requestDto));
    }

    // 가게 삭제
    @DeleteMapping("/api/shops/{id}")
    public ResponseEntity<Void> deleteShop(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        shopService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    // --- 관리자 전용 API ---

    // 관리자 입점 대기 목록 조회
    @GetMapping("/api/admin/shops")
    public ResponseEntity<List<ShopResponse>> getAdminShops(
        @RequestParam(required = false) ShopStatus status) {
        Long adminUserId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(shopService.findAllByStatus(adminUserId, status));
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
}
