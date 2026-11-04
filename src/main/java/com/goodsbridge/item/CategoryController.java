package com.goodsbridge.item;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    // 1. POST /api/categories — 카테고리 생성
    @PostMapping("/categories")
    public ResponseEntity<Long> createCategory(@RequestBody CategoryCreateRequest request) {
        Long categoryId = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryId);
    }

    // 2. GET /api/categories — 카테고리 목록
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getCategories() {
        List<CategoryResponse> categories = categoryService.findAll();
        return ResponseEntity.ok(categories);
    }
}

