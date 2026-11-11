package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
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
    private final ShopRepository shopRepository; // [추가] 가게 소유자 및 존재 여부 확인용
    private final ShopSubscriptionService shopSubscriptionService;

    private static final int MAX_PAGE_SIZE = 100; // 페이징 최대 허용 사이즈 제한

    // 상품 등록 : 구독 여부 검증 -> 카테고리 검증 -> 상품 정보 저장
    @Transactional
    public Long create(Long userId, ItemCreateRequest request) {
        request.validate();

        // 가게 ID가 있을 때만 가게 상품으로 보고 소유자/구독 검증
        // (가게 ID 없이 들어오는 건 교환 화면에서 일반 유저가 등록하는 교환용 상품 — 기획서 3.2 예외)
        if (request.getShopId() != null) {
            // 💡 [동시성 방어 통일] update와 동일하게 비관적 락(findByIdWithLock) 적용
            Shop shop = shopRepository.findByIdWithLock(request.getShopId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

            if (!shop.isOwner(userId)) {
                throw new ForbiddenException("본인 소유의 가게에만 상품을 등록할 수 있습니다.");
            }

            // [추가] 구독 활성화 여부 필수 검증
            if (!shopSubscriptionService.isSubscriptionActive(request.getShopId())) {
                throw new ForbiddenException("구독이 활성화된 가게만 상품을 등록할 수 있습니다.");
            }
        }

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

    // 전체 상품 목록 조회 필터링 검색 (제목 검색어 없음)
    public Page<ItemResponse> findAll(Long categoryId, String region, SaleType saleType, Pageable pageable) {
        return findAll(categoryId, region, saleType, null, pageable);
    }

    // 전체 상품 목록 조회 필터링 검색 (페이징 크기 제한 및 최신순 기본 정렬 적용, keyword 는 제목에 포함된 글자로 검색)
    public Page<ItemResponse> findAll(Long categoryId, String region, SaleType saleType, String keyword, Pageable pageable) {
        String titleKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        if (titleKeyword != null && titleKeyword.length() > 100) {
            titleKeyword = titleKeyword.substring(0, 100);
        }
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

        Page<Item> itemPage = itemRepository.searchItemsWithFilterAndKeyword(categoryId, region, saleType, titleKeyword, pageable);
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

        if (request.getShopId() == null) {
            // 가게 없는 상품(교환용)은 가게 ID 없이 수정 가능. 가게 상품은 가게를 떼어낼 수 없음
            if (item.getShopId() != null) {
                throw new BadRequestException("상품 수정 시 가게 선택은 필수입니다.");
            }
        } else {
            // [동시성 방어] 샵 상태 및 구독 변경 동시 충돌 방지를 위해 비관적 락(findByIdWithLock) 적용
            Shop shop = shopRepository.findByIdWithLock(request.getShopId())
                .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

            // [추가] 수정하려는 가게의 소유자 검증
            if (!shop.isOwner(userId)) {
                throw new ForbiddenException("본인 소유의 가게로만 상품을 지정하거나 수정할 수 있습니다.");
            }

            // [추가] 수정 시에도 구독 활성화 상태 필수 검증
            if (!shopSubscriptionService.isSubscriptionActive(request.getShopId())) {
                throw new ForbiddenException("구독이 활성화된 가게의 상품만 수정할 수 있습니다.");
            }
        }

        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 카테고리입니다."));

        // 💡 [필드 반영] Item 엔티티의 update 메서드 시그니처에 맞춰 첫 번째 인자로 shopId 전달
        item.update(
            request.getShopId(),
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



    // 특정 샵의 구독 재개/복구 시, 만료로 인해 소프트 삭제되었거나
    // 닫혀 있던(CLOSED) 모든 상품들을 일괄 복구하고 ON_SALE 상태로 전환합니다.
    @Transactional
    public void restoreAndActivateItemsByShopId(Long shopId) {
        // 1. 소프트 삭제된 상품들 조회 후 복구 처리
        List<Item> expiredItems = itemRepository.findAllDeletedByShopId(shopId);
        for (Item item : expiredItems) {
            item.restore(ItemStatus.ON_SALE);
        }

        // 2. 삭제되지 않고 단순히 상태만 CLOSED였던 상품들 일괄 상태 변경
        itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.CLOSED, ItemStatus.ON_SALE);
    }
}
