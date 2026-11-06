package com.goodsbridge.shop;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final ShopSubscriptionRepository subscriptionRepository;

    @Transactional
    public ShopResponse create(Long userId, ShopCreateRequest requestDto) {
        requestDto.validate();

        Shop shop = Shop.builder()
            .ownerUserId(userId)
            .name(requestDto.getName())
            .region(requestDto.getRegion())
            .address(requestDto.getAddress())
            .status(ShopStatus.PENDING)
            .build();

        return new ShopResponse(shopRepository.save(shop));
    }

    // 페이징 적용된 디렉터리 조회
    public Page<ShopResponse> findAll(String name, String region, Pageable pageable) {
        return shopRepository.searchApprovedShops(name, region, pageable)
            .map(ShopResponse::new);
    }

    public ShopResponse findById(Long id) {
        return new ShopResponse(findActiveShopEntity(id));
    }

    public Page<ShopResponse> findAllByOwnerUserId(Long userId, Pageable pageable) {
        return shopRepository.findAllByOwnerUserIdAndDeletedFalse(userId, pageable)
            .map(ShopResponse::new);
    }

    @Transactional
    public ShopResponse update(Long userId, Long shopId, ShopUpdateRequest requestDto) {
        requestDto.validate();

        Shop shop = findActiveShopEntity(shopId);
        validateOwner(shop, userId);
        shop.update(requestDto.getName(), requestDto.getRegion(), requestDto.getAddress());
        return new ShopResponse(shop);
    }

    @Transactional
    public void delete(Long userId, Long shopId) {
        Shop shop = findActiveShopEntity(shopId);
        validateOwner(shop, userId);

        LocalDateTime now = LocalDateTime.now();

        // 1. 가게 본인 소프트 삭제 (폐점 상태 전환)
        shop.deleteShop();

        // 2. 해당 가게에 속한 모든 상품 일괄 소프트 삭제 및 판매 마감 (CLOSED)
        itemRepository.softDeleteByShopId(shopId, now);

        // 3. 해당 가게의 활성화된(ACTIVE) 구독이 있다면 함께 소프트 삭제 및 유니크 충돌 방지 처리
        subscriptionRepository.findActiveByShopIdWithLock(shopId).ifPresent(subscription -> {
            subscription.delete(); // paymentId 변형 및 status = EXPIRED, deletedAt 설정
        });
    }
    @Transactional
    public void approve(Long adminUserId, Long shopId) {
        validateAdmin(adminUserId);
        Shop shop = findActiveShopEntity(shopId);
        shop.updateStatus(ShopStatus.APPROVED);
    }

    @Transactional
    public void reject(Long adminUserId, Long shopId) {
        validateAdmin(adminUserId);
        Shop shop = findActiveShopEntity(shopId);
        shop.updateStatus(ShopStatus.REJECTED);
    }

    @Transactional
    public void close(Long adminUserId, Long shopId) {
        validateAdmin(adminUserId);
        Shop shop = findActiveShopEntity(shopId);
        shop.updateStatus(ShopStatus.CLOSED);
    }

    public Page<ShopResponse> searchShopsByAdmin(Long adminUserId, String name, String region, ShopStatus status, Pageable pageable) {
        validateAdmin(adminUserId);
        return shopRepository.searchShopsByAdmin(name, region, status, pageable)
            .map(ShopResponse::new);
    }

    private Shop findActiveShopEntity(Long shopId) {
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));
        if (shop.isDeleted()) {
            throw new NotFoundException("이미 삭제(폐점)된 가게입니다.");
        }
        return shop;
    }

    private void validateOwner(Shop shop, Long userId) {
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인의 가게만 수정 및 삭제할 수 있습니다.");
        }
    }

    private void validateAdmin(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 사용자입니다."));
        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("관리자 권한이 없습니다.");
        }
    }
}
