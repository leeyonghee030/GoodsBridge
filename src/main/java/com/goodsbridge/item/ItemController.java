package com.goodsbridge.item;


import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
public class ItemController {

    private final ItemService itemService;

    /// 1. 상품 등록 (생성된 ID 반환)
    @PostMapping
    public ResponseEntity<Long> createItem(
        @RequestAttribute("userId") Long userId,
        @RequestBody ItemCreateRequest request) {
        Long itemId = itemService.createItem(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(itemId);
    }

    // 2. 목록/검색 조회
    @GetMapping
    public ResponseEntity<Page<ItemResponse>> getItems(
        @RequestParam(required = false) Long category,
        @RequestParam(required = false) String region,
        @RequestParam(required = false) SaleType saleType,
        Pageable pageable) {
        Page<ItemResponse> items = itemService.getItems(category, region, saleType, pageable);
        return ResponseEntity.ok(items);
    }

    // 3. 내 상품 목록 조회
    @GetMapping("/me")
    public ResponseEntity<List<ItemResponse>> getMyItems(
        @RequestAttribute("userId") Long userId) {
        List<ItemResponse> myItems = itemService.getMyItems(userId);
        return ResponseEntity.ok(myItems);
    }

    // 4. 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<ItemResponse> getItem(@PathVariable Long id) {
        ItemResponse item = itemService.getItem(id);
        return ResponseEntity.ok(item);
    }

    // 5. 상품 수정
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateItem(
        @RequestAttribute("userId") Long userId,
        @PathVariable Long id,
        @RequestBody ItemUpdateRequest request) {
        itemService.updateItem(userId, id, request);
        return ResponseEntity.ok().build();
    }

    // 6. 상품 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(
        @RequestAttribute("userId") Long userId,
        @PathVariable Long id) {
        itemService.deleteItem(userId, id);
        return ResponseEntity.ok().build();
    }
}
