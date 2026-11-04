package com.goodsbridge.item;

import com.goodsbridge.item.Category;
import com.goodsbridge.item.CategoryCreateRequest;
import com.goodsbridge.item.CategoryJpaRepository;
import com.goodsbridge.item.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryJpaRepository categoryRepository;

    @Transactional
    public Long create(CategoryCreateRequest request) {
        Category category = Category.builder()
            .name(request.getName())
            .build();
        return categoryRepository.save(category).getId();
    }

    public List<CategoryResponse> findAll() {
        return categoryRepository.findAll().stream()
            .map(CategoryResponse::from)
            .toList();
    }
}
