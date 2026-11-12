package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.chain-code}")
    private String chainCode;

    @Transactional
    public ShopResponse create(Long userId, ShopCreateRequest requestDto) {
        requestDto.validate();
        if (!chainCode.equals(requestDto.getChainCode() == null ? null : requestDto.getChainCode().trim())) {
            throw new BadRequestException("체인점 코드가 올바르지 않습니다.");
        }

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
        // 💡 1. 샵 폐점 시 비관적 락을 적용하여 동시 구독/수정 충돌 원천 차단
        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 가게입니다."));
        if (shop.isDeleted()) {
            throw new NotFoundException("이미 삭제(폐점)된 가게입니다.");
        }
        validateOwner(shop, userId);

        LocalDateTime now = LocalDateTime.now();

        // 2. 가게 본인 소프트 삭제 (폐점 처리)
        shop.deleteShop();

        // 3. 💡 [정합성 보장] 연관된 모든 상품 일괄 소프트 삭제 및 판매 마감 (CLOSED)
        itemRepository.softDeleteByShopId(shopId, now);

        // 4. 💡 [정합성 보장] 활성화된(ACTIVE) 구독이 있다면 소프트 삭제 및 유니크 충돌 방지 처리
        subscriptionRepository.findActiveByShopIdWithLock(shopId).ifPresent(ShopSubscription::delete);
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
        boolean wasApproved = shop.getStatus() == ShopStatus.APPROVED;
        shop.updateStatus(ShopStatus.REJECTED);

        // 승인된 가게의 승인을 취소하면 구독과 판매중 상품도 함께 정리한다 (판매완료 상품은 배송/환불 처리를 위해 유지)
        if (wasApproved) {
            itemRepository.closeOnSaleByShopId(shopId, LocalDateTime.now());
            subscriptionRepository.findActiveByShopIdWithLock(shopId).ifPresent(ShopSubscription::delete);
        }
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
