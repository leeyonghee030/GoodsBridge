package com.goodsbridge.shop;

import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

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

        // 하드 삭제 대신 소프트 삭제 수행 (데이터 보존 및 연관관계 무결성 유지)
        shop.deleteShop();
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
