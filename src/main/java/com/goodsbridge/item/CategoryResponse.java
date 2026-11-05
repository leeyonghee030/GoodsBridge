package com.goodsbridge.item;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private Long itemCount; // [추가] 카테고리별 상품 개수

    public static CategoryResponse of(Category category, Long itemCount) {
        return new CategoryResponse(category.getId(), category.getName(), itemCount);
    }
}
