package com.goodsbridge.purchase;

import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.user.User;
import com.goodsbridge._core.exception.ForbiddenException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.goodsbridge._core.exception.BadRequestException;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock PurchaseRepository purchaseRepository;
    @Mock PaymentService paymentService;
    @Mock EntityManager entityManager;
    @Mock ItemRepository itemRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock ShopRepository shopRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @Mock com.goodsbridge.auction.AuctionRepository auctionRepository;
    @InjectMocks PurchaseService purchaseService;

    @Test
    void findMine_returnsPurchaseDetailsFromRepository() {
        Item item = mock(Item.class);
        when(item.getId()).thenReturn(7L);
        Purchase purchase = Purchase.builder()
            .item(item)
            .paymentId("payment-1")
            .deliveryAddress("Seoul")
            .build();
        when(purchaseRepository.findAllPurchaseByUserId(3L)).thenReturn(List.of(purchase));

        List<PurchaseResponse.DetailDto> result = purchaseService.findMine(3L);

        assertEquals(1, result.size());
        assertEquals(7L, result.get(0).getItemId());
        assertEquals("payment-1", result.get(0).getPaymentId());
        verify(purchaseRepository).findAllPurchaseByUserId(3L);
    }

    @Test
    void createDto_rejectsMissingPaymentId() {
        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setDeliveryAddress("Seoul");
        assertThrows(BadRequestException.class, dto::validate);
    }

    @Test
    void confirm_rejectsNonBuyer() {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(10L);
        Purchase purchase = Purchase.builder()
            .item(mock(Item.class)).user(buyer).paymentId("payment-1")
            .deliveryAddress("Seoul").build();
        when(purchaseRepository.findById(1L)).thenReturn(java.util.Optional.of(purchase));

        assertThrows(ForbiddenException.class, () -> purchaseService.confirm(1L, 20L));
        verifyNoInteractions(paymentService, settlementHistoryService);
    }

    @Test
    void shipping_rejectsUnrelatedUser() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(10L);
        when(item.getShopId()).thenReturn(null);
        Purchase purchase = Purchase.builder()
            .item(item).user(mock(User.class)).paymentId("payment-1")
            .deliveryAddress("Seoul").build();
        when(purchaseRepository.findById(1L)).thenReturn(java.util.Optional.of(purchase));

        PurchaseRequest.ShippingDto dto = new PurchaseRequest.ShippingDto();
        dto.setTrackingNumber("TRACK-1");

        assertThrows(ForbiddenException.class, () -> purchaseService.shipping(1L, 20L, dto));
    }

    @Test
    void purchase_verifiesPaymentAndSavesPurchase() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getPrice()).thenReturn(10_000);
        when(item.getStatus()).thenReturn(ItemStatus.ON_SALE);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));
        when(purchaseRepository.existsByUserIdAndItemId(3L, 7L)).thenReturn(false);
        User buyer = mock(User.class);
        when(entityManager.getReference(User.class, 3L)).thenReturn(buyer);

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        Purchase saved = Purchase.builder().item(item).user(buyer).paymentId("payment-1")
            .deliveryAddress("Seoul").build();
        when(purchaseRepository.save(any(Purchase.class))).thenReturn(saved);

        PurchaseResponse.DetailDto result = purchaseService.purchase(7L, 3L, dto);

        assertEquals("payment-1", result.getPaymentId());
        verify(paymentService).verify("payment-1", 10_000,
            com.goodsbridge.payment.PaymentPurpose.PURCHASE, 3L);
        verify(purchaseRepository).save(any(Purchase.class));
        // 정상 구매는 자동 취소 대상이 아니다.
        verify(paymentService, never()).cancelUnrecorded(any(), any());
    }

    @Test
    void purchase_rejectsDuplicatePurchaseBeforePaymentVerification() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));
        when(purchaseRepository.existsByUserIdAndItemId(3L, 7L)).thenReturn(true);

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        assertThrows(BadRequestException.class, () -> purchaseService.purchase(7L, 3L, dto));
        // 결제 검증은 하지 않지만, 이미 결제된 건은 자동 취소를 요청한다.
        verify(paymentService, never()).verify(any(), anyInt(), any(), any());
        verify(paymentService).cancelUnrecorded(eq("payment-1"), anyString());
    }

    @Test
    void purchase_rejectsAuctionSaleTypeItemBeforePaymentVerification() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getStatus()).thenReturn(ItemStatus.ON_SALE);
        when(item.getSaleType()).thenReturn(com.goodsbridge.item.SaleType.AUCTION);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        assertThrows(BadRequestException.class, () -> purchaseService.purchase(7L, 3L, dto));
        verify(paymentService, never()).verify(any(), anyInt(), any(), any());
        verify(purchaseRepository, never()).save(any(Purchase.class));
    }

    @Test
    void purchase_rejectsEventSaleTypeItemBeforePaymentVerification() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getStatus()).thenReturn(ItemStatus.ON_SALE);
        when(item.getSaleType()).thenReturn(com.goodsbridge.item.SaleType.EVENT);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        assertThrows(BadRequestException.class, () -> purchaseService.purchase(7L, 3L, dto));
        verify(paymentService, never()).verify(any(), anyInt(), any(), any());
        verify(purchaseRepository, never()).save(any(Purchase.class));
    }

    @Test
    void purchase_rejectsItemAlreadySoldToAnotherBuyerBeforePaymentVerification() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));
        when(purchaseRepository.existsByUserIdAndItemId(3L, 7L)).thenReturn(false);
        when(purchaseRepository.existsByItemId(7L)).thenReturn(true);

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> purchaseService.purchase(7L, 3L, dto));

        assertEquals("이미 판매된 상품입니다.", e.getMessage());
        verify(paymentService, never()).verify(any(), anyInt(), any(), any());
        verify(paymentService).cancelUnrecorded(eq("payment-1"), anyString());
    }

    @Test
    void purchase_convertsUniqueViolationOnSaveToBadRequest() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getPrice()).thenReturn(10_000);
        when(item.getStatus()).thenReturn(ItemStatus.ON_SALE);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));
        when(entityManager.getReference(User.class, 3L)).thenReturn(mock(User.class));
        // existsByItemId 검사를 동시에 통과한 경우 -> uk_purchase_item 위반
        when(purchaseRepository.save(any(Purchase.class))).thenThrow(new DataIntegrityViolationException("dup"));

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> purchaseService.purchase(7L, 3L, dto));

        assertEquals("이미 판매된 상품입니다.", e.getMessage());
        // 결제 검증은 통과했지만 저장에 실패했으므로 PortOne 결제를 자동 취소한다.
        verify(paymentService).cancelUnrecorded(eq("payment-1"), anyString());
    }

    @Test
    void confirm_isIdempotentWhenAlreadyConfirmed() {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(10L);
        Purchase purchase = Purchase.builder()
            .item(mock(Item.class)).user(buyer).paymentId("payment-1")
            .deliveryAddress("Seoul").build();
        purchase.ship("TRACK-1");
        purchase.markDelivered();
        purchase.confirm();
        when(purchaseRepository.findById(1L)).thenReturn(java.util.Optional.of(purchase));

        purchaseService.confirm(1L, 10L);

        verifyNoInteractions(paymentService, settlementHistoryService);
    }


    @Test
    void purchase_rejectsItemThatIsNotOnSaleAndCancelsPayment() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getStatus()).thenReturn(ItemStatus.CLOSED);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> purchaseService.purchase(7L, 3L, dto));

        assertEquals("판매 중인 상품이 아닙니다.", e.getMessage());
        verify(paymentService, never()).verify(any(), anyInt(), any(), any());
        verify(purchaseRepository, never()).save(any(Purchase.class));
        verify(paymentService).cancelUnrecorded(eq("payment-1"), anyString());
    }

    @Test
    void purchase_rejectsTooLongDeliveryAddressAndCancelsPayment() {
        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("a".repeat(256));

        assertThrows(BadRequestException.class, () -> purchaseService.purchase(7L, 3L, dto));

        verifyNoInteractions(itemRepository, purchaseRepository);
        verify(paymentService).cancelUnrecorded(eq("payment-1"), anyString());
    }

    @Test
    void purchase_cancelsPaymentWhenPaymentVerificationItselfFailsAfterPortOnePaid() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getPrice()).thenReturn(10_000);
        when(item.getStatus()).thenReturn(ItemStatus.ON_SALE);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));
        // 개발자 도구로 결제 금액을 바꾼 경우: PortOne 은 PAID 인데 서버 금액 검증에서 거절된다.
        when(paymentService.verify("payment-1", 10_000,
            PaymentPurpose.PURCHASE, 3L)).thenThrow(new BadRequestException("결제 금액이 일치하지 않습니다."));

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> purchaseService.purchase(7L, 3L, dto));

        assertEquals("결제 금액이 일치하지 않습니다.", e.getMessage());
        verify(purchaseRepository, never()).save(any(Purchase.class));
        verify(paymentService).cancelUnrecorded(eq("payment-1"), anyString());
    }

    @Test
    void createDto_rejectsTooLongDeliveryAddress() {
        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("a".repeat(256));

        assertThrows(BadRequestException.class, dto::validate);
    }

    // ---------- 배송 ----------

    @Test
    void shipping_setsTrackingNumberAndMarksDelivered() {
        Purchase purchase = purchaseOwnedBySeller(10L);
        PurchaseRequest.ShippingDto dto = new PurchaseRequest.ShippingDto();
        dto.setTrackingNumber("TRACK-1");
        dto.setDeliveryStatus(DeliveryStatus.DELIVERED);

        purchaseService.shipping(1L, 10L, dto);

        assertEquals("TRACK-1", purchase.getTrackingNumber());
        assertEquals(DeliveryStatus.DELIVERED, purchase.getDeliveryStatus());
    }

    @Test
    void shipping_rejectsRequestWithoutTrackingNumberAndStatus() {
        Purchase purchase = purchaseOwnedBySeller(10L);

        assertThrows(BadRequestException.class,
            () -> purchaseService.shipping(1L, 10L, new PurchaseRequest.ShippingDto()));

        assertEquals(DeliveryStatus.PREPARING, purchase.getDeliveryStatus());
    }

    @Test
    void shipping_rejectsStatusChangesThatAreNotSupported() {
        Purchase purchase = purchaseOwnedBySeller(10L);

        PurchaseRequest.ShippingDto preparing = new PurchaseRequest.ShippingDto();
        preparing.setDeliveryStatus(DeliveryStatus.PREPARING);
        assertThrows(BadRequestException.class, () -> purchaseService.shipping(1L, 10L, preparing));

        // 운송장 번호 없이 배송중으로만 바꾸는 요청도 거절한다.
        PurchaseRequest.ShippingDto shippedOnly = new PurchaseRequest.ShippingDto();
        shippedOnly.setDeliveryStatus(DeliveryStatus.SHIPPED);
        assertThrows(BadRequestException.class, () -> purchaseService.shipping(1L, 10L, shippedOnly));

        assertEquals(DeliveryStatus.PREPARING, purchase.getDeliveryStatus());
    }

    @Test
    void shipping_cannotRevertDeliveredPurchaseByResendingTrackingNumber() {
        Purchase purchase = purchaseOwnedBySeller(10L);
        purchase.ship("TRACK-1");
        purchase.markDelivered();

        PurchaseRequest.ShippingDto dto = new PurchaseRequest.ShippingDto();
        dto.setTrackingNumber("TRACK-2");

        assertThrows(BadRequestException.class, () -> purchaseService.shipping(1L, 10L, dto));

        assertEquals(DeliveryStatus.DELIVERED, purchase.getDeliveryStatus());
        assertEquals("TRACK-1", purchase.getTrackingNumber());
    }

    @Test
    void findByShop_allReturnsEveryDeliveryStatus() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(1L)).thenReturn(java.util.Optional.of(shop));
        when(purchaseRepository.findAllByShop(1L, null)).thenReturn(List.of());

        purchaseService.findByShop(1L, 5L, "ALL");

        verify(purchaseRepository).findAllByShop(1L, null);
    }

    @Test
    void findByShop_rejectsUnknownDeliveryStatus() {
        assertThrows(BadRequestException.class, () -> purchaseService.findByShop(1L, 5L, "UNKNOWN"));
        verifyNoInteractions(purchaseRepository);
    }

    // ---------- 수령 확인 / 정산 ----------

    @Test
    void confirm_settlesRemainingAmountAfterPartialRefund() {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(10L);
        Item item = mock(Item.class);
        when(item.getShopId()).thenReturn(1L);
        Purchase purchase = Purchase.builder().item(item).user(buyer).paymentId("payment-1")
            .deliveryAddress("Seoul").build();
        purchase.ship("TRACK-1");
        purchase.markDelivered();
        when(purchaseRepository.findById(1L)).thenReturn(java.util.Optional.of(purchase));

        Shop shop = mock(Shop.class);
        when(shopRepository.findById(1L)).thenReturn(java.util.Optional.of(shop));
        // 10,000원 결제 중 3,000원이 부분 환불된 상태
        Payment payment = Payment.builder().paymentId("payment-1").user(mock(User.class))
            .amount(10_000).purpose(PaymentPurpose.PURCHASE).build();
        payment.refund(3_000);
        when(paymentRepository.findByPaymentId("payment-1")).thenReturn(java.util.Optional.of(payment));

        purchaseService.confirm(1L, 10L);

        assertEquals(PurchaseStatus.CONFIRMED, purchase.getStatus());
        verify(paymentService).release("payment-1");
        // 상품 가격(10,000)이 아니라 환불을 뺀 7,000원으로 정산한다.
        verify(settlementHistoryService).calculate(shop, payment, 7_000, SettlementHistoryType.PURCHASE_FEE);
    }

    // 판매자(개인, shopId 없음)가 소유한 PREPARING 구매를 만들고 조회 스텁까지 설정한다.
    private Purchase purchaseOwnedBySeller(Long sellerUserId) {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(sellerUserId);
        Purchase purchase = Purchase.builder().item(item).user(mock(User.class)).paymentId("payment-1")
            .deliveryAddress("Seoul").build();
        when(purchaseRepository.findById(1L)).thenReturn(java.util.Optional.of(purchase));
        return purchase;
    }


    // ---------- 경매 연동 ----------

    @Test
    void purchase_rejectsItemWithAuctionInProgressAndCancelsPayment() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getStatus()).thenReturn(ItemStatus.ON_SALE);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));
        when(auctionRepository.existsByItemIdAndStatusIn(eq(7L), any())).thenReturn(true);

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> purchaseService.purchase(7L, 3L, dto));

        assertEquals("경매가 진행 중인 상품은 일반 구매할 수 없습니다.", e.getMessage());
        verify(paymentService, never()).verify(any(), anyInt(), any(), any());
        verify(purchaseRepository, never()).save(any(Purchase.class));
        verify(paymentService).cancelUnrecorded(eq("payment-1"), anyString());
    }

    @Test
    void purchase_savesShopSourceAndPaidAmount() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getPrice()).thenReturn(10_000);
        when(item.getStatus()).thenReturn(ItemStatus.ON_SALE);
        when(itemRepository.findById(7L)).thenReturn(java.util.Optional.of(item));
        User buyer = mock(User.class);
        when(entityManager.getReference(User.class, 3L)).thenReturn(buyer);
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PurchaseRequest.CreateDto dto = new PurchaseRequest.CreateDto();
        dto.setPaymentId("payment-1");
        dto.setDeliveryAddress("Seoul");

        purchaseService.purchase(7L, 3L, dto);

        org.mockito.ArgumentCaptor<Purchase> captor = org.mockito.ArgumentCaptor.forClass(Purchase.class);
        verify(purchaseRepository).save(captor.capture());
        assertEquals(PurchaseSource.SHOP, captor.getValue().getSource());
        assertEquals(10_000, captor.getValue().getAmount());
    }

    @Test
    void findMine_showsShopAndAuctionPurchasesWithSourceTitleAndAmount() {
        Item shopItem = mock(Item.class);
        when(shopItem.getId()).thenReturn(7L);
        when(shopItem.getTitle()).thenReturn("상점 굿즈");
        Item auctionItem = mock(Item.class);
        when(auctionItem.getId()).thenReturn(8L);
        when(auctionItem.getTitle()).thenReturn("경매 굿즈");
        Purchase shopPurchase = Purchase.builder().item(shopItem).paymentId("pay-shop")
            .deliveryAddress("Seoul").source(PurchaseSource.SHOP).amount(10_000).build();
        Purchase auctionPurchase = Purchase.builder().item(auctionItem).paymentId("pay-auction")
            .deliveryAddress("Seoul").source(PurchaseSource.AUCTION).amount(35_000).build();
        when(purchaseRepository.findAllPurchaseByUserId(3L)).thenReturn(List.of(auctionPurchase, shopPurchase));

        List<PurchaseResponse.DetailDto> result = purchaseService.findMine(3L);

        assertEquals(PurchaseSource.AUCTION, result.get(0).getSource());
        assertEquals("경매 굿즈", result.get(0).getItemTitle());
        assertEquals(35_000, result.get(0).getAmount());
        assertEquals(PurchaseSource.SHOP, result.get(1).getSource());
    }

    @Test
    void purchaseBuilderDefaultsToShopSource() {
        Purchase purchase = Purchase.builder().item(mock(Item.class)).paymentId("p").deliveryAddress("Seoul").build();

        assertEquals(PurchaseSource.SHOP, purchase.getSource());
        assertFalse(purchase.isAuction());
    }

    @Test
    void confirm_auctionPurchaseReleasesPaymentButDoesNotSettleAgain() {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(10L);
        Item item = mock(Item.class);
        Purchase purchase = Purchase.builder().item(item).user(buyer).paymentId("balance-1")
            .deliveryAddress("Seoul").source(PurchaseSource.AUCTION).amount(35_000).build();
        purchase.ship("TRACK-1");
        purchase.markDelivered();
        when(purchaseRepository.findById(1L)).thenReturn(java.util.Optional.of(purchase));

        purchaseService.confirm(1L, 10L);

        assertEquals(PurchaseStatus.CONFIRMED, purchase.getStatus());
        verify(paymentService).release("balance-1");
        // 경매 정산(수수료 포함)은 잔금 결제 때 이미 기록했으므로 PURCHASE_FEE 를 또 만들지 않는다
        verifyNoInteractions(settlementHistoryService);
    }
}
