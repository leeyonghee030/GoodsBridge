package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.shop.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final CategoryJpaRepository categoryRepository;
    private final ShopRepository shopRepository;
    private final ShopSubscriptionRepository shopSubscriptionRepository;

    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 상품 등록
     * 가게 확인 -> 소유자 확인 -> 구독 확인 -> 카테고리 확인 -> 상품 저장
     */
    @Transactional
    public Long create(Long userId, ItemCreateRequest request) {
        request.validate();

        if (request.getShopId() == null) {
            throw new BadRequestException("상품을 등록하려면 가게 선택이 필수입니다.");
        }

        Shop shop = shopRepository.findByIdWithLock(request.getShopId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        validateShop(shop, userId);

        if (!isSubscriptionActive(request.getShopId())) {
            throw new ForbiddenException("구독이 활성화된 가게만 상품을 등록할 수 있습니다.");
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

    /**
     * 전체 상품 목록 조회 및 필터링 검색
     */
    public Page<ItemResponse> findAll(
        Long categoryId,
        String region,
        SaleType saleType,
        Pageable pageable
    ) {
        int pageSize = Math.min(pageable.getPageSize(), MAX_PAGE_SIZE);

        Sort sort = pageable.getSort().isUnsorted()
            ? Sort.by(Sort.Direction.DESC, "createdAt")
            : pageable.getSort();

        pageable = PageRequest.of(
            pageable.getPageNumber(),
            pageSize,
            sort
        );

        Page<Item> itemPage = itemRepository.searchItemsWithFilter(
            categoryId,
            region,
            saleType,
            pageable
        );

        return itemPage.map(ItemResponse::from);
    }

    /**
     * 상품 상세 조회
     */
    public ItemResponse findById(Long id) {
        validateId(id);

        Item item = itemRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() ->
                new NotFoundException("존재하지 않거나 삭제된 상품입니다."));

        return ItemResponse.from(item);
    }

    /**
     * 판매자 본인의 상품 목록 조회
     */
    public List<ItemResponse> findAllByUserId(Long userId) {
        return itemRepository.findBySellerUserIdAndDeletedFalse(userId)
            .stream()
            .map(ItemResponse::from)
            .toList();
    }

    /**
     * 상품 정보 수정
     */
    @Transactional
    public void update(Long userId, Long itemId, ItemUpdateRequest request) {
        validateId(itemId);
        request.validate();

        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() ->
                new NotFoundException("존재하지 않거나 삭제된 상품입니다."));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException("해당 상품에 대한 권한이 없습니다.");
        }

        if (request.getShopId() == null) {
            throw new BadRequestException("상품 수정 시 가게 선택은 필수입니다.");
        }

        Shop shop = shopRepository.findByIdWithLock(request.getShopId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        validateShop(shop, userId);

        if (!isSubscriptionActive(request.getShopId())) {
            throw new ForbiddenException("구독이 활성화된 가게의 상품만 수정할 수 있습니다.");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 카테고리입니다."));

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

    /**
     * 상품 삭제
     */
    @Transactional
    public void delete(Long userId, Long itemId) {
        validateId(itemId);

        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() ->
                new NotFoundException("존재하지 않거나 삭제된 상품입니다."));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException("해당 상품에 대한 권한이 없습니다.");
        }

        item.deleteItem();
    }

    /**
     * 상품 등록 및 수정이 가능한 가게인지 검증
     */
    private void validateShop(Shop shop, Long userId) {
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException(
                "본인 소유의 가게에서만 상품을 등록하거나 수정할 수 있습니다."
            );
        }

        if (shop.isDeleted()) {
            throw new ForbiddenException("삭제된 가게에서는 상품을 관리할 수 없습니다.");
        }

        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new ForbiddenException("승인된 가게에서만 상품을 관리할 수 있습니다.");
        }
    }

    /**
     * ID 유효성 검사
     */
    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new BadRequestException("유효하지 않은 ID 값입니다.");
        }
    }

    /**
     * 구독 활성 여부 확인
     */
    private boolean isSubscriptionActive(Long shopId) {
        return shopSubscriptionRepository
            .existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );
    }

    // ==================================================
    // 구독 이벤트 리스너
    // ==================================================

    /**
     * 구독 활성화 후 상품 판매 재개
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        readOnly = false
    )
    public void handleShopSubscriptionActivated(
        ShopSubscriptionActivatedEvent event
    ) {
        resumeItemsByShopId(event.getShopId());
    }

    /**
     * 구독 취소 후 상품 판매 중지
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        readOnly = false
    )
    public void handleShopSubscriptionCanceled(
        ShopSubscriptionCanceledEvent event
    ) {
        pauseItemsByShopId(event.getShopId());
    }

    /**
     * 구독 만료 후 상품 판매 중지
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(
        propagation = Propagation.REQUIRES_NEW,
        readOnly = false
    )
    public void handleShopSubscriptionExpired(
        ShopSubscriptionExpiredEvent event
    ) {
        pauseItemsByShopId(event.getShopId());
    }

    // ==================================================
    // 상품 판매 상태 변경
    // ==================================================

    /**
     * 구독 재활성화에 따른 상품 판매 재개
     */
    private void resumeItemsByShopId(Long shopId) {

        Shop shop = shopRepository.findById(shopId)
            .orElse(null);

        if (shop == null
            || shop.isDeleted()
            || shop.getStatus() != ShopStatus.APPROVED) {
            return;
        }

        int updatedCount = itemRepository.resumeItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE
        );

        log.info(
            "구독 재활성화 상품 판매 재개 완료 - shopId: {}, count: {}",
            shopId,
            updatedCount
        );
    }

    /**
     * 구독 취소 및 만료에 따른 상품 판매 중지
     */
    private void pauseItemsByShopId(Long shopId) {

        Shop shop = shopRepository.findById(shopId)
            .orElse(null);

        if (shop == null || shop.isDeleted()) {
            return;
        }

        int updatedCount = itemRepository.pauseItemsBySubscription(
            shopId,
            ItemStatus.ON_SALE,
            ItemStatus.CLOSED
        );

        log.info(
            "구독 중지 상품 판매 중지 완료 - shopId: {}, count: {}",
            shopId,
            updatedCount
        );
    }
}
