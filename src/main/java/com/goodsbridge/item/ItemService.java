package com.goodsbridge.item;

import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemJpaRepository itemRepository;
    private final CategoryJpaRepository categoryRepository;


    // 상품 등록 : 카테코리 여부 검증 -> 상품 정보 저장
    @Transactional
    public Long create(Long userId, ItemCreateRequest request) {
        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND));

        Item item = Item.builder()
            .sellerUserId(userId)
            .shopId(request.getShopId())
            .category(category)
            .title(request.getTitle())
            .description(request.getDescription())
            .price(request.getPrice())
            .saleType(request.getSaleType())
            .status(ItemStatus.ON_SALE)
            .imageUrl(request.getImageUrl())
            .build();

        return itemRepository.save(item).getId();
    }

    // 전체 상품 목록 조회 필터링 검색
    public Page<ItemResponse> findAll(Long categoryId, SaleType saleType, Pageable pageable) {
        Page<Item> itemPage = itemRepository.searchItemsWithFilter(categoryId,  saleType, pageable);
        return itemPage.map(ItemResponse::from);
    }

    // 상품 단건 상세 조회
    public ItemResponse findById(Long id) {
        Item item = itemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ITEM_NOT_FOUND));
        return ItemResponse.from(item);
    }

    // 내 상품 목록 조회
    public List<ItemResponse> findAllByUserId(Long userId) {
        return itemRepository.findBySellerUserId(userId).stream()
            .map(ItemResponse::from)
            .toList();
    }

    // 상품 정보 수정
    @Transactional
    public void update(Long userId, Long itemId, ItemUpdateRequest request) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ITEM_NOT_FOUND));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN_ITEM_ACCESS);
        }

        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new NotFoundException(ErrorCode.CATEGORY_NOT_FOUND));

        item.update(
            category,
            request.getTitle(),
            request.getDescription(),
            request.getPrice(),
            request.getSaleType(),
            request.getStatus(),
            request.getImageUrl()
        );
    }

    // 상품 삭제
    @Transactional
    public void delete(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ITEM_NOT_FOUND));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN_ITEM_ACCESS);
        }

        itemRepository.delete(item);
    }
}
