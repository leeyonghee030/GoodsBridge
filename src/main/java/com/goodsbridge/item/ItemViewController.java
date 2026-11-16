package com.goodsbridge.item;

import com.goodsbridge._core.util.SecurityUtil;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class ItemViewController {

    private final ItemService itemService;
    private final ShopRepository shopRepository;
    private final ShopSaleService shopSaleService;

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    // TODO(데모 종료 후 제거): 데모 모드에서는 상품 구매 결제창을 건너뛴다 (서버는 DemoPaymentService 가 처리)
    @Value("${app.demo.enabled:false}")
    private boolean skipPayment;

    @GetMapping("/test/item")
    public String index(Model model) {
        model.addAttribute("items", itemService.findShopItems(null, null, null, null, null, PageRequest.of(0, 60)).getContent());
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "item/index";
    }

    @GetMapping("/test/item/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ItemResponse item = itemService.findById(id);
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        // 판매자 본인이거나 이 가게 주인이면 구매 버튼을 막는다 (결제가 먼저 되고 서버에서 거절되는 일을 막기 위해 화면에서 미리 차단)
        boolean ownItem = userId != null && (userId.equals(item.getSellerUserId())
            || (item.getShopId() != null && shopRepository.findById(item.getShopId()).map(shop -> shop.isOwner(userId)).orElse(false)));
        model.addAttribute("item", item);
        model.addAttribute("ownItem", ownItem);
        // 가게 계정은 다른 가게 상품도 살 수 없다 (서버에서도 거절하지만 버튼부터 막는다)
        model.addAttribute("shopAccount", userId != null && shopRepository.existsByOwnerUserIdAndDeletedFalse(userId));
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("skipPayment", skipPayment);
        model.addAttribute("userId", userId);
        return "item/detail";
    }

    // 마이페이지: 구매 내역
    @GetMapping("/my/purchases")
    public String myPurchases(Model model) {
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "item/purchases";
    }

    // 가게 사장님 마이페이지: 판매 상품 관리 (상품 등록 + 판매중/판매완료 목록)
    @GetMapping("/shop/items")
    public String shopItems(Model model) {
        Long userId = SecurityUtil.getCurrentUserIdOrNull();
        List<Shop> shops = userId == null ? List.of()
            : shopRepository.findAllByOwnerUserIdAndDeletedFalse(userId, PageRequest.of(0, 20)).getContent();
        // 가게 사장님이 아니면 개인 마이페이지로 돌려보낸다
        if (shops.isEmpty()) {
            return "redirect:/update";
        }
        model.addAttribute("userId", userId);
        model.addAttribute("shops", shops);
        return "item/shop";
    }

    // 상품 등록
    @PostMapping("/api/items")
    public ResponseEntity<Long> createItem(@RequestBody ItemCreateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        Long itemId = itemService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(itemId);
    }

    // 상품 목록 조회 및 검색
    @GetMapping("/api/items")
    public ResponseEntity<Page<ItemResponse>> getItems(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String region,
        @RequestParam(required = false) SaleType saleType,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "false") boolean shopOnly,   // SHOP 목록: 가게 상품만 (판매중 먼저)
        @RequestParam(required = false) ItemStatus status,
        Pageable pageable) {
        Page<ItemResponse> items = shopOnly
            ? itemService.findShopItems(categoryId, region, saleType, keyword, status, pageable)
            : itemService.findAll(categoryId, region, saleType, keyword, pageable);
        return ResponseEntity.ok(items);
    }

    // 카테고리별 상품 조회
    @GetMapping("/api/categories/{categoryId}/items")
    public ResponseEntity<Page<ItemResponse>> getItemsByCategory(
        @PathVariable Long categoryId,
        @RequestParam(required = false) String region,
        @RequestParam(required = false) SaleType saleType,
        Pageable pageable) {

        Page<ItemResponse> items = itemService.findAll(categoryId, region , saleType, pageable);
        return ResponseEntity.ok(items);
    }

    // 내 상품 목록 조회
    @GetMapping("/api/items/me")
    public ResponseEntity<List<ItemResponse>> getMyItems() {
        Long userId = SecurityUtil.getCurrentUserId();
        List<ItemResponse> myItems = itemService.findAllByUserId(userId);
        return ResponseEntity.ok(myItems);
    }

    // 구매자: 내 구매 내역 (배송/확정/환불)
    @GetMapping("/api/items/my-purchases")
    public ResponseEntity<List<ShopSaleResponse>> getMyPurchases() {
        return ResponseEntity.ok(shopSaleService.findMyPurchases(SecurityUtil.getCurrentUserId()));
    }

    // 가게 사장님: 내 가게 상품 판매 내역 (구매자/배송/환불)
    @GetMapping("/api/items/shop-sales")
    public ResponseEntity<List<ShopSaleResponse>> getMyShopSales() {
        return ResponseEntity.ok(shopSaleService.findMySales(SecurityUtil.getCurrentUserId()));
    }

    // 상품 상세 조회
    @GetMapping("/api/items/{id}")
    public ResponseEntity<ItemResponse> getItem(@PathVariable Long id) {
        ItemResponse item = itemService.findById(id);
        return ResponseEntity.ok(item);
    }

    // 상품 수정
    @PatchMapping("/api/items/{id}")
    public ResponseEntity<Void> updateItem(
        @PathVariable Long id,
        @RequestBody ItemUpdateRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        itemService.update(userId, id, request);
        return ResponseEntity.ok().build();
    }

    // 상품 삭제
    @DeleteMapping("/api/items/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        itemService.delete(userId, id);
        return ResponseEntity.ok().build();
    }
}
