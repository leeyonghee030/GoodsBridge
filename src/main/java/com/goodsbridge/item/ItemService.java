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

    @Transactional
    public Long createItem(Long userId, ItemCreateRequest request) {
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

    public Page<ItemResponse> getItems(Long categoryId, String region, SaleType saleType, Pageable pageable) {
        Page<Item> itemPage = itemRepository.searchItemsWithFilter(categoryId, region, saleType, pageable);
        return itemPage.map(ItemResponse::from);
    }

    public ItemResponse getItem(Long id) {
        Item item = itemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ITEM_NOT_FOUND));
        return ItemResponse.from(item);
    }

    @Transactional
    public void updateItem(Long userId, Long itemId, ItemUpdateRequest request) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ITEM_NOT_FOUND));

        // 권한 없음: 403 Forbidden
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

    @Transactional
    public void deleteItem(Long userId, Long itemId) {
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ITEM_NOT_FOUND));

        // 권한 없음: 403 Forbidden
        if (!item.isOwner(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN_ITEM_ACCESS);
        }

        itemRepository.delete(item);
    }

    public List<ItemResponse> getMyItems(Long userId) {
        return itemRepository.findBySellerUserId(userId).stream()
            .map(ItemResponse::from)
            .toList();
    }
}
