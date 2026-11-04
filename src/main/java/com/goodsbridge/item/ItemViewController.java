package com.goodsbridge.item;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ItemViewController {

    private final ItemService itemService;

    // 상품 등록
    // TODO - 로그인검사는 인터셉터에 등록해서 사용할거 같아서 구현안해놨는데
    // @RequestAtrribute 사용할려면 인터셉터 부분에서 setAttribute로 userId 등록해줘야 사용할수 있음
    @PostMapping("/api/items")
    public ResponseEntity<Long> createItem(
        @RequestAttribute("userId") Long userId,
        @RequestBody ItemCreateRequest request) {
        Long itemId = itemService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(itemId);
    }

    // 상품 목록 조회 및 검색
    @GetMapping("/api/items")
    public ResponseEntity<Page<ItemResponse>> getItems(
        @RequestParam(required = false) Long category,
        @RequestParam(required = false) String region,
        @RequestParam(required = false) SaleType saleType,
        Pageable pageable) {
        Page<ItemResponse> items = itemService.findAll(category, saleType, pageable);
        return ResponseEntity.ok(items);
    }

    // 내 상품 목록 조회
    @GetMapping("/api/items/me")
    public ResponseEntity<List<ItemResponse>> getMyItems(
        @RequestAttribute("userId") Long userId) {
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
        @RequestAttribute("userId") Long userId,
        @PathVariable Long id,
        @RequestBody ItemUpdateRequest request) {
        itemService.update(userId, id, request);
        return ResponseEntity.ok().build();
    }

    // 상품 삭제
    @DeleteMapping("/api/items/{id}")
    public ResponseEntity<Void> deleteItem(
        @RequestAttribute("userId") Long userId,
        @PathVariable Long id) {
        itemService.delete(userId, id);
        return ResponseEntity.ok().build();
    }
}
