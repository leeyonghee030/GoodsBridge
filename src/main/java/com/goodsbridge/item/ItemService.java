package com.goodsbridge.item;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.shop.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
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

    // 상품 등록 : 구독 여부 검증 -> 카테고리 검증 -> 상품 정보 저장
    @Transactional
    public Long create(Long userId, ItemCreateRequest request) {
        request.validate();

        if (request.getShopId() == null) {
            throw new BadRequestException("상품을 등록하려면 가게 선택이 필수입니다.");
        }

        Shop shop = shopRepository.findByIdWithLock(request.getShopId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 소유의 가게에만 상품을 등록할 수 있습니다.");
        }

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

    // 전체 상품 목록 조회 필터링 검색
    public Page<ItemResponse> findAll(Long categoryId, String region, SaleType saleType, Pageable pageable) {
        int pageSize = pageable.getPageSize();

        if (pageSize > MAX_PAGE_SIZE) {
            pageSize = MAX_PAGE_SIZE;
        }

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

    // 내 상품 목록 조회
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
            throw new BadRequestException("상품 수정 시 가게 선택은 필수입니다.");
        }

        Shop shop = shopRepository.findByIdWithLock(request.getShopId())
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 소유의 가게로만 상품을 지정하거나 수정할 수 있습니다.");
        }

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

    // 상품 삭제
    @Transactional
    public void delete(Long userId, Long itemId) {
        validateId(itemId);

        Item item = itemRepository.findByIdAndDeletedFalse(itemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않거나 삭제된 상품입니다."));

        if (!item.isOwner(userId)) {
            throw new ForbiddenException("해당 상품에 대한 권한이 없습니다.");
        }

        item.deleteItem();
    }

    private void validateId(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("유효하지 않은 ID 값입니다.");
        }
    }

    private boolean isSubscriptionActive(Long shopId) {
        return shopSubscriptionRepository.findByShopId(shopId)
            .map(sub -> sub.getStatus() == ShopSubscriptionStatus.ACTIVE
                && sub.getEndDate() != null
                && !sub.getEndDate().isBefore(LocalDate.now()))
            .orElse(false);
    }

    /**
     * 💡 [구독 활성화 이벤트 리스너]
     * - AFTER_COMMIT: 구독 트랜잭션이 완전히 성공(COMMIT)된 직후에 실행됩니다.
     * - REQUIRES_NEW: 독립된 새로운 트랜잭션에서 안전하게 상품 상태를 변경합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleShopSubscriptionActivated(ShopSubscriptionActivatedEvent event) {
        restoreAndActivateItemsByShopId(event.getShopId());
        log.info("[구독 활성화 이벤트 처리 완료] 가게 ID: {}의 상품들이 복구 및 ON_SALE로 전환되었습니다.", event.getShopId());
    }

    /**
     * 💡 [구독 취소 이벤트 리스너 추가]
     * - 구독이 취소된 경우 커밋 직후 상품들을 CLOSED 상태로 전환합니다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleShopSubscriptionCanceled(ShopSubscriptionCanceledEvent event) {
        closeItemsByShopId(event.getShopId());
        log.info("[구독 취소 이벤트 처리 완료] 가게 ID: {}의 상품들이 CLOSED(판매 중지) 상태로 전환되었습니다.", event.getShopId());
    }

    /**
     * 💡 [방어 가드 적용] 폐점되거나 존재하지 않는 샵의 상품 부활 원천 차단
     */
    @Transactional
    public void restoreAndActivateItemsByShopId(Long shopId) {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null || shop.isDeleted()) {
            log.warn("[상품 복구 스킵] 존재하지 않거나 이미 폐점(삭제)된 샵입니다. shopId: {}", shopId);
            return;
        }

        List<Item> expiredItems = itemRepository.findAllDeletedByShopId(shopId);
        for (Item item : expiredItems) {
            item.restore(ItemStatus.ON_SALE);
        }

        itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.CLOSED, ItemStatus.ON_SALE);
    }

    /**
     * 💡 [방어 가드 적용] 폐점되거나 존재하지 않는 샵의 상품 판매 중지 스킵
     */
    @Transactional
    public void closeItemsByShopId(Long shopId) {
        Shop shop = shopRepository.findById(shopId).orElse(null);
        if (shop == null || shop.isDeleted()) {
            log.warn("[상품 상태 변경 스킵] 존재하지 않거나 이미 폐점(삭제)된 샵입니다. shopId: {}", shopId);
            return;
        }

        itemRepository.updateStatusByShopIdAndStatus(shopId, ItemStatus.ON_SALE, ItemStatus.CLOSED);
    }
}
