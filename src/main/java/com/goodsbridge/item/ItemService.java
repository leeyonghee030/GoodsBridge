package com.goodsbridge.item;

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

    private final ItemRepository itemRepository;
    private final CategoryJpaRepository categoryRepository;


    // 상품 등록 : 카테코리 여부 검증 -> 상품 정보 저장
    @Transactional
    public Long create(Long userId, ItemCreateRequest request) {
        request.validate();

        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 카테고리입니다."));

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
    public Page<ItemResponse> findAll(Long categoryId,String region,SaleType saleType, Pageable pageable) {
        Page<Item> itemPage = itemRepository.searchItemsWithFilter(categoryId, region, saleType, pageable);
        return itemPage.map(ItemResponse::from);
    }

    // 상품 단건 상세 조회
    public ItemResponse findById(Long id) {
        validateId(id);

        Item item = itemRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 상품입니다."));
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
        validateId(itemId);
        request.validate();

        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 상품입니다."));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException("해당 상품에 대한 권한이 없습니다.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 카테고리입니다."));

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
        validateId(itemId);

        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 상품입니다."));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException("해당 상품에 대한 권한이 없습니다.");
        }

        itemRepository.delete(item);
    }

    // 공통 ID 검증 메서드
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("유효하지 않은 ID 값입니다.");
        }
    }
}
