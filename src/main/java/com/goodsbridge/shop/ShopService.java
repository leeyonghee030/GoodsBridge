package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final ShopSubscriptionRepository subscriptionRepository;

    /**
     * 가게 등록
     */
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

    /**
     * 승인된 가게 디렉터리 조회
     */
    public Page<ShopResponse> findAll(
        String name,
        String region,
        Pageable pageable
    ) {
        return shopRepository
            .searchApprovedShops(name, region, pageable)
            .map(ShopResponse::new);
    }

    /**
     * 가게 상세 조회
     */
    public ShopResponse findById(Long id) {
        return new ShopResponse(findActiveShopEntity(id));
    }

    /**
     * 점주가 등록한 가게 목록 조회
     */
    public Page<ShopResponse> findAllByOwnerUserId(
        Long userId,
        Pageable pageable
    ) {
        return shopRepository
            .findAllByOwnerUserIdAndDeletedFalse(userId, pageable)
            .map(ShopResponse::new);
    }

    /**
     * 가게 정보 수정
     */
    @Transactional
    public ShopResponse update(
        Long userId,
        Long shopId,
        ShopUpdateRequest requestDto
    ) {
        requestDto.validate();

        Shop shop = findActiveShopEntity(shopId);

        validateOwner(shop, userId);

        shop.update(
            requestDto.getName(),
            requestDto.getRegion(),
            requestDto.getAddress()
        );

        return new ShopResponse(shop);
    }

    /**
     * 가게 삭제
     *
     * 가게 자체를 삭제할 때만 소속 상품을 소프트 삭제합니다.
     * 구독 취소 및 만료 처리와는 별개의 기능입니다.
     */
    @Transactional
    public void delete(Long userId, Long shopId) {

        // 1. Shop 비관적 락 조회
        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElseThrow(() ->
                new NotFoundException("존재하지 않는 가게입니다.")
            );

        if (shop.isDeleted()) {
            throw new NotFoundException("이미 삭제(폐점)된 가게입니다.");
        }

        // 2. 가게 소유권 확인
        validateOwner(shop, userId);

        LocalDateTime now = LocalDateTime.now();

        // 3. 가게 소프트 삭제
        shop.deleteShop();

        // 4. 소속 상품 전체 소프트 삭제
        itemRepository.softDeleteByShopId(
            shopId,
            now,
            ItemStatus.CLOSED
        );

        // 5. 활성 구독이 있다면 실제 소프트 삭제
        // 구독 취소나 만료 이벤트와는 구분합니다.
        subscriptionRepository
            .findActiveByShopIdWithLock(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            )
            .forEach(ShopSubscription::delete);
    }

    /**
     * 가게 승인
     */
    @Transactional
    public void approve(Long adminUserId, Long shopId) {

        validateAdmin(adminUserId);

        Shop shop = shopRepository.findByIdWithLock(shopId)
            .orElseThrow(() ->
                new NotFoundException("존재하지 않는 가게입니다.")
            );

        if (shop.isDeleted()) {
            throw new BadRequestException(
                "이미 삭제(폐점)된 가게는 승인할 수 없습니다."
            );
        }

        if (shop.getStatus() == ShopStatus.APPROVED) {
            throw new BadRequestException(
                "이미 승인 완료된 가게입니다."
            );
        }

        shop.updateStatus(ShopStatus.APPROVED);
    }

    /**
     * 가게 승인 거절
     */
    @Transactional
    public void reject(Long adminUserId, Long shopId) {

        validateAdmin(adminUserId);

        Shop shop = findActiveShopEntity(shopId);

        shop.updateStatus(ShopStatus.REJECTED);
    }

    /**
     * 가게 폐점 상태 변경
     */
    @Transactional
    public void close(Long adminUserId, Long shopId) {

        validateAdmin(adminUserId);

        Shop shop = findActiveShopEntity(shopId);

        shop.updateStatus(ShopStatus.CLOSED);
    }

    /**
     * 관리자용 가게 검색
     */
    public Page<ShopResponse> searchShopsByAdmin(
        Long adminUserId,
        String name,
        String region,
        ShopStatus status,
        Pageable pageable
    ) {
        validateAdmin(adminUserId);

        return shopRepository
            .searchShopsByAdmin(name, region, status, pageable)
            .map(ShopResponse::new);
    }

    /**
     * 삭제되지 않은 가게 조회
     */
    private Shop findActiveShopEntity(Long shopId) {

        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() ->
                new NotFoundException("존재하지 않는 가게입니다.")
            );

        if (shop.isDeleted()) {
            throw new NotFoundException("이미 삭제(폐점)된 가게입니다.");
        }

        return shop;
    }

    /**
     * 가게 소유권 확인
     */
    private void validateOwner(Shop shop, Long userId) {

        if (!shop.isOwner(userId)) {
            throw new ForbiddenException(
                "본인의 가게만 수정 및 삭제할 수 있습니다."
            );
        }
    }

    /**
     * 관리자 권한 확인
     */
    private void validateAdmin(Long userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() ->
                new NotFoundException("존재하지 않는 사용자입니다.")
            );

        if (user.getRole() != Role.ADMIN) {
            throw new ForbiddenException("관리자 권한이 없습니다.");
        }
    }
}
