package com.goodsbridge.purchase;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.auction.AuctionRepository;
import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.item.SaleType;
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
    private final AuctionRepository auctionRepository;

    // 구매 (서버가 거절하면 이미 승인된 PortOne 결제를 자동 취소한다)
    @Transactional
    public PurchaseResponse.DetailDto purchase(Long itemId, Long userId, PurchaseRequest.CreateDto createDto) {
        if (createDto == null) {
            throw new BadRequestException("구매 요청 값이 필요합니다.");
        }
        try {
            return doPurchase(itemId, userId, createDto);
        } catch (RuntimeException e) {
            // 취소 실패는 로그만 남기고 원래 예외를 그대로 던진다 (이미 기록된 결제는 취소되지 않는다)
            paymentService.cancelUnrecorded(createDto.getPaymentId(), "구매 실패로 인한 자동 취소");
            throw e;
        }
    }

    // 구매 본 처리
    private PurchaseResponse.DetailDto doPurchase(Long itemId, Long userId, PurchaseRequest.CreateDto createDto) {
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

        // 판매 중(ON_SALE)이 아닌 상품(종료/숨김 등)은 구매할 수 없다.
        if (item.getStatus() != ItemStatus.ON_SALE) {
            throw new BadRequestException("판매 중인 상품이 아닙니다.");
        }

        // 경매 상품(sale_type = AUCTION)은 일반 구매가 아니라 경매 입찰/잔금 결제로만 구매할 수 있다.
        // (경매가 아직 열리지 않았거나 시드 데이터처럼 경매 기록이 없어도 막는다)
        if (item.getSaleType() == SaleType.AUCTION) {
            throw new BadRequestException("경매 상품은 일반 구매할 수 없습니다. 경매에 입찰해 주세요.");
        }

        // 경매 진행 중이거나 낙찰 후 잔금 대기 중인 상품은 일반 구매할 수 없다 (낙찰자 구매와 충돌 방지)
        if (auctionRepository.existsByItemIdAndStatusIn(itemId,
            List.of(com.goodsbridge.auction.Status.ACTIVE, com.goodsbridge.auction.Status.ENDED_SUCCESS))) {
            throw new BadRequestException("경매가 진행 중인 상품은 일반 구매할 수 없습니다.");
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
                .source(PurchaseSource.SHOP)
                .amount(item.getPrice())
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

        String trackingNumber = shippingDto.getTrackingNumber();
        DeliveryStatus deliveryStatus = shippingDto.getDeliveryStatus();

        // 변경할 값이 없거나 지원하지 않는 상태 변경은 조용히 넘기지 않고 거절한다.
        if (trackingNumber == null && deliveryStatus == null) {
            throw new BadRequestException("운송장 번호 또는 배송 상태 중 하나는 필요합니다.");
        }
        if (deliveryStatus == DeliveryStatus.PREPARING
            || (deliveryStatus == DeliveryStatus.SHIPPED && trackingNumber == null)) {
            throw new BadRequestException("지원하지 않는 배송 상태 변경입니다. 운송장 번호를 입력하면 배송중으로, DELIVERED 로 배송완료 처리됩니다.");
        }

        // 운송 번호 입력 -> 배송중
        if (trackingNumber != null) {
            purchase.ship(trackingNumber);
        }

        // 배송 완료 처리
        if (deliveryStatus == DeliveryStatus.DELIVERED) {
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
        // status 가 비어 있거나 ALL 이면 배송 상태와 무관하게 전체 조회
        if (status != null && !status.isBlank() && !"ALL".equalsIgnoreCase(status)) {
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

        // 경매 낙찰 구매는 잔금 결제 시점에 정산(수수료 포함)이 이미 기록되므로 여기서 다시 정산하지 않는다.
        if (!purchase.isAuction() && purchase.getItem().getShopId() != null) {
            Shop shop = shopRepository.findById(purchase.getItem().getShopId())
                .orElseThrow(() -> new NotFoundException("가게를 찾을 수 없습니다."));
            Payment payment = paymentRepository.findByPaymentId(purchase.getPaymentId())
                .orElseThrow(() -> new NotFoundException("결제 내역을 찾을 수 없습니다."));

            // 부분 환불이 있었다면 환불액을 뺀 실제 정산 금액(결제액 - 환불액)으로 기록한다.
            settlementHistoryService.calculate(
                shop,
                payment,
                payment.getRefundableAmount(),
                SettlementHistoryType.PURCHASE_FEE
            );
        }
    }
}
