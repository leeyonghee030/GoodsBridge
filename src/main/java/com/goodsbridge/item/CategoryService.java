package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.NotFoundException;
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
    private final ItemRepository itemRepository;

    // 카테고리 생성 (관리자만 가능)
    @Transactional
    public Long create(CategoryCreateRequest request) {
        request.validate();

        if (categoryRepository.existsByName(request.getName())) {
            throw new BadRequestException("이미 존재하는 카테고리 이름입니다.");
        }

        Category category = Category.builder()
            .name(request.getName())
            .build();
        return categoryRepository.save(category).getId();
    }

    // 카테고리 목록 조회 (가나다 순 정렬+각 카테고리별 상품 개수 포함)
    public List<CategoryResponse> findAll() {
        List<Category> categories = categoryRepository.findAllByOrderByNameAsc();

        return categories.stream()
            .map(category -> {
                // 삭제되지 않은 유효한 상품 개수만 카운트
                Long itemCount = itemRepository.countByCategoryIdAndDeletedFalse(category.getId());
                return CategoryResponse.of(category, itemCount);
            }).toList();
    }

    // 카테고리 수정 (관리자용)
    @Transactional
    public void update(Long id, CategoryUpdateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("카테고리 이름은 필수입니다.");
        }

        Category category = categoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 카테고리입니다"));

        if (categoryRepository.existsByNameAndIdNot(request.getName(), id)) {
            throw new BadRequestException("이미 존재하는 카테고리 이름입니다.");
        }
        category.updateName(request.getName());
    }

    // 카테고리 삭제 (상품 존재 여부 방어 로직 포함)
    @Transactional
    public void delete(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 카테고리입니다."));

        // 삭제되지 않은 상품이 남아있는 경우 카테고리 삭제 불가
        Long itemCount = itemRepository.countByCategoryIdAndDeletedFalse(categoryId);
        if (itemCount > 0) {
            throw new BadRequestException("상품이 등록되어 있는 카테고리는 삭제할 수 없습니다.");
        }
        categoryRepository.delete(category);
    }
}
