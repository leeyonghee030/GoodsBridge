package com.goodsbridge.purchase;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.user.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final PaymentRepository paymentRepository;
    private final EntityManager entityManager;
    private final ItemRepository itemRepository;
    private final ShopRepository shopRepository;
    private final SettlementHistoryService settlementHistoryService;

    // 구매
    @Transactional
    public PurchaseResponse.DetailDto purchase(Long itemId, Long userId, PurchaseRequest.CreateDto createDto) {
        createDto.validate();

        log.info("구매 처리 시작 - itemId={}, userId={}, paymentId={}",
            itemId, userId, createDto.getPaymentId());

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

        // 다른 사람이 이미 산 상품이면 결제 검증(외부 호출) 전에 차단한다.
        if (purchaseRepository.existsByItemId(itemId)) {
            throw new BadRequestException("이미 판매된 상품입니다.");
        }

        paymentService.verify(createDto.getPaymentId(), item.getPrice(), PaymentPurpose.PURCHASE, userId);

        log.info("PortOne 결제 검증 및 Payment 저장 완료 - itemId={}, userId={}, paymentId={}",
            itemId, userId, createDto.getPaymentId());

        // 구매 기록
        User buyer = entityManager.getReference(User.class, userId);

        Purchase purchase;
        try {
            purchase = purchaseRepository.save(Purchase.builder()
                .item(item)
                .user(buyer)
                .paymentId(createDto.getPaymentId())
                .deliveryAddress(createDto.getDeliveryAddress())
                .build());

            item.updateStatus(ItemStatus.SOLD);

            log.info("구매 저장 완료 - itemId={}, userId={}, paymentId={}",
                itemId, userId, createDto.getPaymentId());
        } catch (DataIntegrityViolationException e) {
            // 위 existsByItemId 검사를 동시에 통과한 경우: uk_purchase_item 제약이 마지막 방어선
            throw new BadRequestException("이미 판매된 상품입니다.");
        }

        log.info("구매 저장 완료 - itemId={}, userId={}, paymentId={}",
            itemId, userId, createDto.getPaymentId());
        return new PurchaseResponse.DetailDto(purchase);
    }

    // 배송
    @Transactional
    public void shipping(Long purchaseId, Long userId, PurchaseRequest.ShippingDto shippingDto) {
        // 구매 내역 조회
        Purchase purchase = purchaseRepository.findById(purchaseId)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 구매 내역입니다."));

        // 사용자가 구매한 상품인지 확인
        Long shopId = purchase.getItem().getShopId();
        boolean seller = purchase.getItem().getSellerUserId().equals(userId);
        boolean shopOwner = shopId != null && shopRepository.findById(shopId)
            .map(shop -> shop.isOwner(userId)).orElse(false);
        if (!seller && !shopOwner) {
            throw new ForbiddenException("상품 판매자 또는 가게 주인만 배송을 관리할 수 있습니다.");
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
        Shop shop = shopRepository.findById(shopId)
            .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
        if (!shop.isOwner(userId)) {
            throw new ForbiddenException("본인 가게의 발송 내역만 조회할 수 있습니다.");
        }
        List<Purchase> purchases = purchaseRepository.findAllByShop(shopId, deliveryStatus);
        return purchases.stream().map(PurchaseResponse.DetailDto::new).toList();
    }

    // 수령 확인
    @Transactional
    public void confirm(Long id, Long userId) {
        // 구매 조회
        Purchase purchase = purchaseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("존재하지 않는 구매 내역입니다."));

        // 권한 체크
        if (!purchase.getUser().getId().equals(userId)) {
            throw new ForbiddenException("본인의 구매 내역만 수령 확인할 수 있습니다.");
        }
        if (purchase.getStatus() == PurchaseStatus.CONFIRMED) return;
        purchase.confirm();
        paymentService.release(purchase.getPaymentId());

        if (purchase.getItem().getShopId() != null) {
            Shop shop = shopRepository.findById(purchase.getItem().getShopId())
                .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
            Payment payment = paymentRepository.findByPaymentId(purchase.getPaymentId())
                .orElseThrow(() -> new NotFoundException("결제 내역을 찾을 수 없습니다."));

            settlementHistoryService.calculate(
                shop,
                payment,
                purchase.getItem().getPrice(),
                SettlementHistoryType.PURCHASE_FEE
            );
        }
    }
}
