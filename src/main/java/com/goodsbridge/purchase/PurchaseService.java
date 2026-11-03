package com.goodsbridge.purchase;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ErrorCode;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PurchaseService {
    private final PurchaseRepository purchaseRepository;
    private final PaymentService paymentService;
    private final EntityManager entityManager;
    private final ItemRepository itemRepository;

    // 구매
    @Transactional
    public PurchaseResponse.DetailDto purchase(Long itemId, Long userId, PurchaseRequest.CreateDto createDto) {
        // 배송지 여부 확인
        if (createDto.getDeliveryAddress() == null || createDto.getDeliveryAddress().isBlank()) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // 조회
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));

        // 구매 여부 확인
        if (item.getSellerUserId().equals(userId)) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // 이미 구매된 상품인지 확인
        if (purchaseRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new BadRequestException(ErrorCode.INVALID_REQUEST);
        }

        // 구매 기록
        paymentService.verify(createDto.getPaymentId(), item.getPrice(), PaymentPurpose.PURCHASE, userId);
        User buyer = entityManager.getReference(User.class, userId);
        Purchase purchase = purchaseRepository.save(Purchase.builder()
            .item(item)
            .user(buyer)
            .paymentId(createDto.getPaymentId())
            .deliveryAddress(createDto.getDeliveryAddress())
            .build());

        return new PurchaseResponse.DetailDto(purchase);
    }

    // 배송
    @Transactional
    public void shipping(Long purchaseId, Long userId, PurchaseRequest.ShippingDto shippingDto) {
        // 구매 내역 조회
        Purchase purchase = purchaseRepository.findById(purchaseId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));

        // 사용자가 구매한 상품인지 확인
        if (!purchase.getItem().getSellerUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }

        // 운송 번호 확인
        if (shippingDto.getTrackingNumber() != null) {
            purchase.ship(shippingDto.getTrackingNumber());
        }

        // 배송 상태 확인
        if (shippingDto.getDeliveryStatus() == DeliveryStatus.DELIVERED) {
            purchase.markDelivered();
        }
    }

    // 내 구매
    public List<PurchaseResponse.DetailDto> findMine(Long userId) {
        List<Purchase> purchaseList = purchaseRepository.findAllPurchaseByUserId(userId);
        return purchaseList.stream().map(PurchaseResponse.DetailDto::new).toList();
    }

    // 가게 발송 관리
    public List<PurchaseResponse.DetailDto> findByShop(Long shopId, Long userId, String status) {
        DeliveryStatus deliveryStatus = null;
        if (status != null && !status.isBlank()) {
            try {
                deliveryStatus = DeliveryStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(ErrorCode.INVALID_REQUEST);
            }
        }
        List<Purchase> purchases = purchaseRepository.findAllByShop(shopId, deliveryStatus);

        // 판매자 권환 확인
        boolean isOwner = purchases.stream().allMatch(purchase ->
            purchase.getItem().getSellerUserId().equals(userId));

        if (!isOwner && !purchases.isEmpty()) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        return purchases.stream().map(PurchaseResponse.DetailDto::new).toList();
    }

    // 수령 확인
    public void confirm(Long id, Long userId) {
        // 구매 조회
        Purchase purchase = purchaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND));

        // 권한 체크
        if (!purchase.getUser().getId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        purchase.confirm();
    }
}
