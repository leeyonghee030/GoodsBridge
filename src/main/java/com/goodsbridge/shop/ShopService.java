package com.goodsbridge.shop;

import com.goodsbridge._core.exception.CustomException;
import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.hibernate.annotations.NotFound;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ShopService {

    private final ShopJpaRepository shopRepository;

    // 입점 신청
    @Transactional
    public ShopResponse create(Long userId, ShopCreateRequest requestDto) {
        Shop shop = Shop.builder()
            .ownerUserId(userId)
            .name(requestDto.getName())
            .region(requestDto.getRegion())
            .address(requestDto.getAddress())
            .build();
        return new ShopResponse(shopRepository.save(shop));
    }

    // 디렉터리 조회 - 승인된 가게 목록 조회
    public List<ShopResponse> findAll(String name, String region) {
        return shopRepository.searchApprovedShops(name, region).stream()
            .map(ShopResponse::new)
            .collect(Collectors.toList());
    }

    // 가게 단건 상세 조회 - ID로 조회
    public ShopResponse findById(Long id) {
        Shop shop = shopRepository.findById(id)
            .orElseThrow(() -> new CustomException(ErrorCode.SHOP_NOT_FOUND) {});
        return new ShopResponse(shop);
    }

    // 내 가게 목록 조회 - 로그인한 가게사장이 운영하는 지점 목록 조회
    public List<ShopResponse> findAllByOwnerUserId(Long userID) {
        return shopRepository.findAllByOwnerUserId(userID).stream()
            .map(ShopResponse::new)
            .collect(Collectors.toList());
    }

    // 가게 수정
    @Transactional
    public ShopResponse update(Long userId, Long shopId, ShopUpdateRequest requestDto) {
        Shop shop = findShopEntity(shopId);
        validateOwner(shop, userId);
        shop.update(requestDto.getName(), requestDto.getRegion(), requestDto.getAddress());
        return new ShopResponse(shop);
    }

    // 가게 삭제
    @Transactional
    public void delete(Long userId, Long shopId) {
        Shop shop = findShopEntity(shopId);
        validateOwner(shop, userId);
        shopRepository.delete(shop);
    }

    // 관리자 입점 승인
    @Transactional
    public void approve(Long shopId) {
        Shop shop = findShopEntity(shopId);
        shop.updateStatus(ShopStatus.APPROVED);
    }

    // 관리자 입점 거절
    @Transactional
    public void reject(Long shopId) {
        Shop shop = findShopEntity(shopId);
        shop.updateStatus(ShopStatus.REJECTED);
    }

    // 관리자 입점 대기 목록 조회 - 상태별로 가게 목록 조회
    public List<ShopResponse> findAllByStatus(ShopStatus status) {
        return shopRepository.findAllByStatus(status).stream()
            .map(ShopResponse::new)
            .collect(Collectors.toList());
    }

    // 편의 메서드

    // 엔티티 가져오기
    private Shop findShopEntity(Long shopId) {
        return shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.SHOP_NOT_FOUND));
    }

    //유효성 검사
    private void validateOwner(Shop shop, Long userId) {
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN_SHOP_ACCESS);
        }
    }
}
