package com.goodsbridge.purchase;

import com.goodsbridge._core.exception.BadRequestException;
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
            throw new BadRequestException("배송지 주소가 필요합니다.");
        }

        // 조회
        Item item = itemRepository.findById(itemId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 상품입니다."));

        // 구매 여부 확인
        if (item.getSellerUserId().equals(userId)) {
            throw new BadRequestException("본인이 등록한 상품은 구매할 수 없습니다.");
        }

        // 이미 구매된 상품인지 확인
        if (purchaseRepository.existsByUserIdAndItemId(userId, itemId)) {
            throw new BadRequestException("이미 구매한 상품입니다.");
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
            .orElseThrow(() -> new NotFoundException("존재하지 않는 구매 내역입니다."));

        // 사용자가 구매한 상품인지 확인
        if (!purchase.getItem().getSellerUserId().equals(userId)) {
            throw new ForbiddenException("본인이 판매한 상품의 배송만 관리할 수 있습니다.");
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
                throw new BadRequestException("올바르지 않은 배송 상태 값입니다.");
            }
        }
        List<Purchase> purchases = purchaseRepository.findAllByShop(shopId, deliveryStatus);

        // 판매자 권환 확인
        boolean isOwner = purchases.stream().allMatch(purchase ->
            purchase.getItem().getSellerUserId().equals(userId));

        if (!isOwner && !purchases.isEmpty()) {
            throw new ForbiddenException("본인 가게의 발송 내역만 조회할 수 있습니다.");
        }
        return purchases.stream().map(PurchaseResponse.DetailDto::new).toList();
    }

    // 수령 확인
    public void confirm(Long id, Long userId) {
        // 구매 조회
        Purchase purchase = purchaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 구매 내역입니다."));

        // 권한 체크
        if (!purchase.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인의 구매 내역만 수령 확인할 수 있습니다.");
        }
        purchase.confirm();
    }
}
