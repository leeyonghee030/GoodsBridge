package com.goodsbridge.item;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // POST /api/categories — 카테고리 생성 (관리자만 가능)
    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> createCategory(@RequestBody CategoryCreateRequest request) {
        Long categoryId = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryId);
    }

    // GET /api/categories — 카테고리 목록 조회
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        List<CategoryResponse> categories = categoryService.findAll();
        return ResponseEntity.ok(categories);
    }

    // PATCH /api/categories/{id} - 카테고리 수정 (관리자만 가능)
    @PatchMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateCategory(
        @PathVariable Long id,
        @RequestBody CategoryUpdateRequest request) {
        categoryService.update(id, request);
        return ResponseEntity.ok().build();
    }


    // DELETE /api/categories/{id} - 카테고리 삭제 (관리자만 가능)
    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.ok().build();
    }
}

