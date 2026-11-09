package com.goodsbridge.item;

import com.goodsbridge._core.util.SecurityUtil;
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

    @Value("${portone.store-id}")
    private String storeId;

    @Value("${portone.channel-key}")
    private String channelKey;

    @GetMapping("/test/item")
    public String index(Model model) {
        model.addAttribute("items", itemService.findAll(null, null, null, PageRequest.of(0, 60)).getContent());
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "item/index";
    }

    @GetMapping("/test/item/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("item", itemService.findById(id));
        model.addAttribute("portoneStoreId", storeId);
        model.addAttribute("portoneChannelKey", channelKey);
        model.addAttribute("userId", SecurityUtil.getCurrentUserIdOrNull());
        return "item/detail";
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
        Pageable pageable) {
        Page<ItemResponse> items = itemService.findAll(categoryId, region, saleType, pageable);
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
