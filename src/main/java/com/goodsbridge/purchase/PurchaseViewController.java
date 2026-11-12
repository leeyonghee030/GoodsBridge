package com.goodsbridge.purchase;

import com.goodsbridge._core.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class PurchaseViewController {
    private final PurchaseService purchaseService;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    @GetMapping("/test/purchase")
    public String index(Model model) {
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        return "purchase/index";
    }

    // 구매 요청 ( 배송지 입력 + paymentId 검증 -> 대금 HOLD)
    // http://localhost:8080/api/purchases/me
    @PostMapping("/api/items/{id}/purchase")
    public ResponseEntity<PurchaseResponse.DetailDto> purchase(
        @PathVariable Long id,
        @RequestBody PurchaseRequest.CreateDto createDto) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(purchaseService.purchase(id, userId, createDto));
    }

    // 구매자 수령 확인 -> 판매자에게 RELEASE
    @PostMapping("/api/purchases/{id}/confirm")
    public ResponseEntity<Void> confirm(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        purchaseService.confirm(id, userId);
        return ResponseEntity.ok().build();
    }

    // 내 구매 내역
    @GetMapping("/api/purchases/me")
    public ResponseEntity<List<PurchaseResponse.DetailDto>> myPurchases() {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(purchaseService.findMine(userId));
    }

    // 가게 발송 관리 목록 (status: PREPARING / SHIPPED / DELIVERED / ALL, 기본 PREPARING)
    @GetMapping("/api/shops/{id}/purchases")
    public ResponseEntity<List<PurchaseResponse.DetailDto>> shopPurchases(
        @PathVariable Long id, @RequestParam(defaultValue = "PREPARING") String status) {
        Long userId = SecurityUtil.getCurrentUserId();
        return ResponseEntity.ok(purchaseService.findByShop(id, userId, status));
    }

    // 운송장 번호 입력 / 배송상태 변경
    @PatchMapping("/api/purchases/{id}/shipping")
    public ResponseEntity<Void> shipping(
        @PathVariable Long id, @RequestBody PurchaseRequest.ShippingDto shippingDto) {
        Long userId = SecurityUtil.getCurrentUserId();
        purchaseService.shipping(id, userId, shippingDto);
        return ResponseEntity.ok().build();
    }
}
