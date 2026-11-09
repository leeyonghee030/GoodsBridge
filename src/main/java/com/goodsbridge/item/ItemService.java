package com.goodsbridge.item;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.shop.ShopSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryJpaRepository categoryRepository;
    private final ShopSubscriptionService shopSubscriptionService;

    private static final int MAX_PAGE_SIZE = 100; // 페이징 최대 허용 사이즈 제한

    // 상품 등록 : 구독 여부 검증 -> 카테고리 검증 -> 상품 정보 저장
    @Transactional
    public Long create(Long userId, ItemCreateRequest request) {
        if (request.getShopId() != null && !shopSubscriptionService.isSubscriptionActive(request.getShopId())) {
            throw new ForbiddenException("구독이 활성화된 가게만 상품을 등록할 수 있습니다.");
        }

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

    // 전체 상품 목록 조회 필터링 검색 (페이징 크기 제한 및 최신순 기본 정렬 적용)
    public Page<ItemResponse> findAll(Long categoryId, String region, SaleType saleType, Pageable pageable) {
        int pageSize = pageable.getPageSize();

        // 1. 페이징 사이즈 방어 로직 (최대 100개 제한)
        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }

        // 2. 정렬 조건이 지정되지 않았다면 기본적으로 최신 등록순(createdAt DESC) 부여
        Sort sort = pageable.getSort().isUnsorted()
            ? Sort.by(Sort.Direction.DESC, "createdAt")
            : pageable.getSort();

        pageable = PageRequest.of(pageable.getPageNumber(), pageSize, sort);

        Page<Item> itemPage = itemRepository.searchItemsWithFilter(categoryId, region, saleType, pageable);
        return itemPage.map(ItemResponse::from);
    }

    // 상품 단건 상세 조회
    public ItemResponse findById(Long id) {
        validateId(id);

        Item item = itemRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않거나 삭제된 상품입니다."));
        return ItemResponse.from(item);
    }

    // 내 상품 목록 조회 (삭제된 상품 제외)
    public List<ItemResponse> findAllByUserId(Long userId) {
        return itemRepository.findBySellerUserIdAndDeletedFalse(userId).stream()
            .map(ItemResponse::from)
            .toList();
    }

    // 상품 정보 수정
    @Transactional
    public void update(Long userId, Long itemId, ItemUpdateRequest request) {
        validateId(itemId);
        request.validate();

        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않거나 삭제된 상품입니다."));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException("해당 상품에 대한 권한이 없습니다.");
        }

        if (item.getShopId() != null && !shopSubscriptionService.isSubscriptionActive(item.getShopId())) {
            throw new ForbiddenException("구독이 활성화된 가게의 상품만 수정할 수 있습니다.");
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

        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않거나 삭제된 상품입니다."));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException("해당 상품에 대한 권한이 없습니다.");
        }

        // 소프트 삭제 메서드 호출
        item.deleteItem();
    }

    // 공통 ID 검증 메서드
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("유효하지 않은 ID 값입니다.");
        }
    }

    @Transactional
    public void closeItemsByShopId(Long shopId) {
        itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.CLOSED, ItemStatus.ON_SALE);
    }

    // ItemService 내부에 추가해야 할 메서드 예시
    @Transactional
    public void restoreAndActivateItemsByShopId(Long shopId) {
        List<Item> expiredItems = itemRepository.findAllDeletedByShopId(shopId);
        for (Item item : expiredItems) {
            item.restore(ItemStatus.ON_SALE);
        }
        itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.CLOSED, ItemStatus.ON_SALE);
    }
}
