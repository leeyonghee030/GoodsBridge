package com.goodsbridge.refund;

import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.Payment;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.settlement.SettlementHistoryService;
import com.goodsbridge.shop.Shop;
import com.goodsbridge.shop.ShopRepository;
import com.goodsbridge.shop.ShopSubscriptionService;
import com.goodsbridge.settlement.SettlementHistoryType;
import com.goodsbridge.purchase.Purchase;
import com.goodsbridge.purchase.PurchaseRepository;
import com.goodsbridge.purchase.PurchaseStatus;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import com.goodsbridge.user.Role;
import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {
    @Mock RefundRepository refundRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentService paymentService;
    @Mock UserRepository userRepository;
    @Mock ShopRepository shopRepository;
    @Mock SettlementHistoryService settlementHistoryService;
    @Mock PurchaseRepository purchaseRepository;
    @Mock com.goodsbridge.auction.AuctionRepository auctionRepository;
    @Mock com.goodsbridge.settlement.SettlementHistoryRepository settlementHistoryRepository;
    @Mock ShopSubscriptionService shopSubscriptionService;
    @Mock ApplicationEventPublisher publisher;
    @InjectMocks RefundService refundService;

    @Test
    void request_setsPurchaseTypeAndRejectsAmountAboveRemainingBalance() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        payment.refund(3_000);

        RefundRequest request = request("p-1", 8_000, "상품 환불");
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> refundService.request(1L, request));
        verifyNoInteractions(refundRepository);
    }

    @Test
    void request_persistsPurchaseRefundType() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        RefundRequest request = request("p-1", 3_000, "상품 환불");
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        Purchase purchase = Purchase.builder().item(mock(Item.class)).user(user).paymentId("p-1")
            .deliveryAddress("Seoul").build();
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        refundService.request(1L, request);

        ArgumentCaptor<Refund> captor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(captor.capture());
        assertEquals(RefundType.PURCHASE, captor.getValue().getType());
    }

    @Test
    void request_allowsRefundAfterDelivery() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        Purchase purchase = Purchase.builder().item(mock(Item.class)).user(user).paymentId("p-1")
            .deliveryAddress("Seoul").build();
        purchase.ship("TRACK-1");
        purchase.markDelivered();
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));
        when(userRepository.getReferenceById(1L)).thenReturn(user);
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        refundService.request(1L, request("p-1", 3_000, "상품 하자"));

        verify(refundRepository).save(any(Refund.class));
    }

    @Test
    void request_rejectsRefundWhileShipping() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        Purchase purchase = Purchase.builder().item(mock(Item.class)).user(user).paymentId("p-1")
            .deliveryAddress("Seoul").build();
        purchase.ship("TRACK-1");   // 배송중
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));

        assertThrows(BadRequestException.class,
            () -> refundService.request(1L, request("p-1", 3_000, "단순 변심")));
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void request_rejectsPaymentWhoseRefundWasRejectedBefore() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        when(refundRepository.existsRejectedByPaymentId("p-1")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> refundService.request(1L, request("p-1", 3_000, "다시 요청")));
        verify(refundRepository, never()).save(any());
    }

    @Test
    void approve_rejectsNonAdmin() {
        User user = mock(User.class);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(user.getRole()).thenReturn(Role.USER);

        assertThrows(ForbiddenException.class, () -> refundService.approve(1L, 7L));
        verifyNoInteractions(paymentService);
    }

    private static RefundRequest request(String paymentId, int amount, String reason) {
        RefundRequest request = new RefundRequest();
        ReflectionTestUtils.setField(request, "paymentId", paymentId);
        ReflectionTestUtils.setField(request, "requestedAmount", amount);
        ReflectionTestUtils.setField(request, "reason", reason);
        return request;
    }

    @Test
    void approve_marksRefundApprovedBeforeCallingPortOneAsTheLastStep() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));

        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("환불")
            .requestedAmount(3_000).status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

        // PortOne 취소가 호출되는 시점에는 DB 쪽 상태 변경이 이미 끝나 있어야 한다.
        doAnswer(invocation -> {
            assertEquals(Status.APPROVED, refund.getStatus());
            return null;
        }).when(paymentService).refund("p-1", 3_000, "환불");

        refundService.approve(1L, 7L);

        verify(paymentService).refund("p-1", 3_000, "환불");
        // 승인 알림은 "환불 거절"이 아니라 REFUND_APPROVED 이고, 눌렀을 때 구매 내역(REFUND)으로 간다
        verify(publisher).publishEvent(argThat((Object e) -> e instanceof com.goodsbridge.notification.NotificationEvent n
            && n.type() == com.goodsbridge.notification.Notification.Type.REFUND_APPROVED
            && n.referenceType() == com.goodsbridge.notification.Notification.ReferenceType.REFUND));
    }

    @Test
    void approve_doesNotCallPortOneWhenRefundIsNotPending() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));

        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("환불")
            .requestedAmount(3_000).status(Status.REJECTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));

        assertThrows(BadRequestException.class, () -> refundService.approve(1L, 7L));

        verifyNoInteractions(paymentService);
    }

    @Test
    void approve_rejectsWhenDeliveryStartedAfterRequest() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));

        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("환불")
            .requestedAmount(10_000).status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

        // 요청 이후 판매자가 발송한 상황
        Purchase purchase = Purchase.builder().item(mock(Item.class)).user(mock(User.class))
            .paymentId("p-1").deliveryAddress("Seoul").build();
        purchase.ship("TRACK-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));

        assertThrows(BadRequestException.class, () -> refundService.approve(1L, 7L));

        assertEquals(Status.REQUESTED, refund.getStatus());
        verifyNoInteractions(paymentService);
    }

    @Test
    void refundByShop_rejectsNonOwnerWithForbidden() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(false);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));

        com.goodsbridge.refund.ShopRefundRequest request = new com.goodsbridge.refund.ShopRefundRequest();
        ReflectionTestUtils.setField(request, "paymentId", "p-1");
        ReflectionTestUtils.setField(request, "requestedAmount", 1_000);
        ReflectionTestUtils.setField(request, "reason", "불량");

        assertThrows(ForbiddenException.class, () -> refundService.refundByShop(5L, 10L, request));
        verifyNoInteractions(refundRepository, paymentService);
    }

    @Test
    void refundAndSettleNoShow_recordsSettlementBeforeCallingPortOne() {
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.EXCHANGE_DEPOSIT).build();
        Shop shop = mock(Shop.class);
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        when(shopRepository.findById(5L)).thenReturn(Optional.of(shop));

        refundService.refundAndSettleNoShow("p-1", 5L, 2_000, "노쇼");

        InOrder inOrder = inOrder(settlementHistoryService, paymentService);
        inOrder.verify(settlementHistoryService).calculate(shop, payment, 2_000, SettlementHistoryType.NO_SHOW_FEE);
        inOrder.verify(paymentService).refund("p-1", 2_000, "노쇼");
    }

    @Test
    void refundAndSettleNoShow_rejectsAmountAboveRefundable() {
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.EXCHANGE_DEPOSIT).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class,
            () -> refundService.refundAndSettleNoShow("p-1", 5L, 10_001, "노쇼"));

        verifyNoInteractions(paymentService, settlementHistoryService);
        assertTrue(payment.getRefundableAmount() == 10_000);
    }


    @Test
    void reject_locksRefundRowAndStoresRejectReason() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        Refund refund = pendingRefund(3_000);
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));

        refundService.reject(7L, 1L, "상품 상태 확인 불가");

        assertEquals(Status.REJECTED, refund.getStatus());
        assertEquals("상품 상태 확인 불가", refund.getRejectReason());
        // approve 와 동시에 실행돼도 덮어쓰지 않도록 잠금 조회만 사용한다.
        verify(refundRepository, never()).findById(any());
        verifyNoInteractions(paymentService);
    }

    @Test
    void reject_requiresReason() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        Refund refund = pendingRefund(3_000);
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));

        assertThrows(BadRequestException.class, () -> refundService.reject(7L, 1L, " "));

        assertEquals(Status.REQUESTED, refund.getStatus());
    }

    @Test
    void reject_cannotRejectAlreadyApprovedRefund() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        Refund refund = pendingRefund(3_000);
        refund.approve();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));

        assertThrows(BadRequestException.class, () -> refundService.reject(7L, 1L, "사유"));

        assertEquals(Status.APPROVED, refund.getStatus());
    }

    @Test
    void approve_fullRefundReopensSoldItemAndMarksPurchaseRefunded() {
        Item item = mock(Item.class);
        when(item.getStatus()).thenReturn(ItemStatus.SOLD);
        when(item.getShopId()).thenReturn(null);   // 가게 상품이 아니면 구독과 무관하게 다시 연다
        Purchase purchase = fullRefundSetup(item);

        refundService.approve(1L, 7L);

        verify(item).updateStatus(ItemStatus.ON_SALE);
        assertEquals(PurchaseStatus.REFUNDED, purchase.getStatus());
        verify(purchaseRepository, never()).delete(any());
    }

    @Test
    void approve_fullRefundDoesNotReopenItemThatSellerClosed() {
        Item item = mock(Item.class);
        when(item.getStatus()).thenReturn(ItemStatus.CLOSED);
        Purchase purchase = fullRefundSetup(item);

        refundService.approve(1L, 7L);

        verify(item, never()).updateStatus(any());
        assertEquals(PurchaseStatus.REFUNDED, purchase.getStatus());
        verify(purchaseRepository, never()).delete(any());
    }

    @Test
    void approve_fullRefundReopensShopItemWhenSubscriptionActive() {
        Item item = mock(Item.class);
        when(item.getStatus()).thenReturn(ItemStatus.SOLD);
        when(item.getShopId()).thenReturn(5L);
        when(shopSubscriptionService.isSubscriptionActive(5L)).thenReturn(true);
        Purchase purchase = fullRefundSetup(item);

        refundService.approve(1L, 7L);

        verify(item).updateStatus(ItemStatus.ON_SALE);
        verify(item, never()).deleteItem();
        assertEquals(PurchaseStatus.REFUNDED, purchase.getStatus());
        verify(purchaseRepository, never()).delete(any());
    }

    @Test
    void approve_fullRefundKeepsShopItemClosedWhenSubscriptionExpired() {
        Item item = mock(Item.class);
        when(item.getStatus()).thenReturn(ItemStatus.SOLD);
        when(item.getShopId()).thenReturn(5L);
        when(shopSubscriptionService.isSubscriptionActive(5L)).thenReturn(false);
        Purchase purchase = fullRefundSetup(item);

        refundService.approve(1L, 7L);

        verify(item).pauseBySubscription();
        verify(item, never()).deleteItem();
        verify(item, never()).updateStatus(ItemStatus.ON_SALE);
        assertEquals(PurchaseStatus.REFUNDED, purchase.getStatus());
        verify(purchaseRepository, never()).delete(any());
    }

    // 10,000원 전액 환불 승인 상황을 구성한다 (PortOne 취소 성공 시 Payment 가 전액 환불 처리됨).
    private Purchase fullRefundSetup(Item item) {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));

        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("환불")
            .requestedAmount(10_000).status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        doAnswer(invocation -> {
            payment.refund(10_000);
            return null;
        }).when(paymentService).refund("p-1", 10_000, "환불");

        Purchase purchase = Purchase.builder().item(item).user(mock(User.class)).paymentId("p-1")
            .deliveryAddress("Seoul").build();
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));
        return purchase;
    }

    private static Refund pendingRefund(int amount) {
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        return Refund.builder().payment(payment).user(mock(User.class)).reason("환불")
            .requestedAmount(amount).status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
    }


    // ---------- 경매: 보증금 단독 환불 불가, 낙찰 상품은 전액 환불만 가능 ----------

    @Test
    void request_rejectsAuctionDepositPaymentOnItsOwn() {
        User user = mock(User.class);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000).purpose(PaymentPurpose.AUCTION_DEPOSIT).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> refundService.request(1L, request("p-1", 3_000, "단순 변심")));

        assertEquals("경매 보증금은 낙찰 상품을 전액 환불할 때 함께 환불돼요.", e.getMessage());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void request_rejectsPartialRefundOfAuctionBalance() {
        User user = mock(User.class);
        Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000).purpose(PaymentPurpose.AUCTION_BALANCE).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        when(user.getId()).thenReturn(1L);

        BadRequestException e = assertThrows(BadRequestException.class,
            () -> refundService.request(1L, request("p-1", 3_000, "단순 변심")));

        assertEquals("경매 낙찰 상품은 낙찰가 전액(보증금 포함)만 환불할 수 있어요.", e.getMessage());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void approve_rejectsAuctionPaymentEvenIfRequestWasCreatedBefore() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(7L)).thenReturn(Optional.of(admin));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.AUCTION_BALANCE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("환불")
            .requestedAmount(3_000).status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

        assertThrows(BadRequestException.class, () -> refundService.approve(1L, 7L));

        verifyNoInteractions(paymentService);
    }

    @Test
    void request_rejectsPaymentsThatAreNeitherPurchaseNorAuctionBalance() {
        for (PaymentPurpose purpose : java.util.List.of(
            PaymentPurpose.EXCHANGE_DEPOSIT, PaymentPurpose.EVENT_FEE, PaymentPurpose.SHOP_SUBSCRIPTION)) {
            User user = mock(User.class);
            when(user.getId()).thenReturn(1L);
            Payment payment = Payment.builder().paymentId("p-1").user(user).amount(10_000).purpose(purpose).build();
            when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

            BadRequestException e = assertThrows(BadRequestException.class,
                () -> refundService.request(1L, request("p-1", 3_000, "단순 변심")));

            assertEquals("구매 또는 경매 낙찰 결제만 환불을 요청할 수 있어요.", e.getMessage());
        }
        verify(refundRepository, never()).save(any());
    }

    // ---------- 가게 주인의 환불 승인/거절 + 거절 재검토 ----------

    private Purchase shopPurchase(Long shopId, String paymentId) {
        Item item = mock(Item.class);
        lenient().when(item.getShopId()).thenReturn(shopId);
        return Purchase.builder().item(item).paymentId(paymentId).deliveryAddress("Seoul").amount(10_000).build();
    }

    @Test
    void approveByShop_reapprovesRejectedRefundAndCancelsPayment() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("파손")
            .requestedAmount(3_000).status(Status.REJECTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        ReflectionTestUtils.setField(refund, "rejectedBy", RejectedBy.SHOP);
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        Purchase shopSale = shopPurchase(10L, "p-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(shopSale));

        refundService.approveByShop(5L, 10L, 1L);

        assertEquals(Status.APPROVED, refund.getStatus());
        verify(paymentService).refund("p-1", 3_000, "파손");
    }

    @Test
    void approveByShop_cannotUndoAlreadyApprovedRefund() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("파손")
            .requestedAmount(3_000).status(Status.APPROVED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        Purchase shopSale = shopPurchase(10L, "p-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(shopSale));

        assertThrows(BadRequestException.class, () -> refundService.approveByShop(5L, 10L, 1L));

        verifyNoInteractions(paymentService);
    }

    @Test
    void rejectByShop_forbiddenForOtherShopOwner() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(9L)).thenReturn(false);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("파손")
            .requestedAmount(3_000).status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        Purchase shopSale = shopPurchase(10L, "p-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(shopSale));

        assertThrows(ForbiddenException.class, () -> refundService.rejectByShop(9L, 10L, 1L, "사유"));

        assertEquals(Status.REQUESTED, refund.getStatus());
    }

    @Test
    void rejectByShop_changesReasonOfAlreadyRejectedRefund() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("파손")
            .requestedAmount(3_000).status(Status.REJECTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        ReflectionTestUtils.setField(refund, "rejectReason", "이전 사유");
        ReflectionTestUtils.setField(refund, "rejectedBy", RejectedBy.SHOP);
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        Purchase shopSale = shopPurchase(10L, "p-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(shopSale));

        refundService.rejectByShop(5L, 10L, 1L, "새 사유");

        assertEquals("새 사유", refund.getRejectReason());
        assertEquals(Status.REJECTED, refund.getStatus());
    }

    @Test
    void refundByShop_refundsWholeAuctionPriceAndCancelsSettlement() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment balance = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(8_000)
            .purpose(PaymentPurpose.AUCTION_BALANCE).build();
        Payment deposit = Payment.builder().paymentId("dep-1").user(mock(User.class)).amount(2_000)
            .purpose(PaymentPurpose.AUCTION_DEPOSIT).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(balance));
        when(paymentRepository.findByPaymentIdLock("dep-1")).thenReturn(Optional.of(deposit));
        when(paymentRepository.findByPaymentId("dep-1")).thenReturn(Optional.of(deposit));
        com.goodsbridge.auction.Auction auction = mock(com.goodsbridge.auction.Auction.class);
        when(auction.getWinningPaymentId()).thenReturn("dep-1");
        when(auctionRepository.findByBalancePaymentId("p-1")).thenReturn(Optional.of(auction));
        Item item = mock(Item.class);
        when(item.getShopId()).thenReturn(10L);
        Purchase purchase = Purchase.builder().item(item).paymentId("p-1").user(mock(User.class)).deliveryAddress("Seoul")
            .source(com.goodsbridge.purchase.PurchaseSource.AUCTION).amount(10_000).build();
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));
        when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

        com.goodsbridge.refund.ShopRefundRequest request = new com.goodsbridge.refund.ShopRefundRequest();
        ReflectionTestUtils.setField(request, "paymentId", "p-1");
        ReflectionTestUtils.setField(request, "requestedAmount", 10_000);   // 잔금 8,000 + 보증금 2,000
        ReflectionTestUtils.setField(request, "reason", "배송 중 파손");

        refundService.refundByShop(5L, 10L, request);

        verify(paymentService).refund("p-1", 8_000, "배송 중 파손");
        verify(paymentService).refund("dep-1", 2_000, "배송 중 파손");
        verify(settlementHistoryRepository).deleteByPaymentAndType(balance, SettlementHistoryType.AUCTION_SALE);
    }

    @Test
    void refundByShop_rejectsAuctionPurchasePayment() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.AUCTION_BALANCE).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));
        Item item = mock(Item.class);
        when(item.getShopId()).thenReturn(10L);
        Purchase purchase = Purchase.builder().item(item).paymentId("p-1").user(mock(User.class)).deliveryAddress("Seoul")
            .source(com.goodsbridge.purchase.PurchaseSource.AUCTION).amount(10_000).build();
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));

        com.goodsbridge.refund.ShopRefundRequest request = new com.goodsbridge.refund.ShopRefundRequest();
        ReflectionTestUtils.setField(request, "paymentId", "p-1");
        ReflectionTestUtils.setField(request, "requestedAmount", 1_000);
        ReflectionTestUtils.setField(request, "reason", "불량");

        assertThrows(BadRequestException.class, () -> refundService.refundByShop(5L, 10L, request));

        verify(refundRepository, never()).save(any());
    }

    @Test
    void refundByShop_appliesPartialRefundImmediatelyWithoutSettlement() {
        Shop shop = mock(Shop.class);
        User shopOwner = mock(User.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        when(userRepository.getReferenceById(5L)).thenReturn(shopOwner);

        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        when(paymentRepository.findByPaymentIdLock("p-1")).thenReturn(Optional.of(payment));

        Item item = mock(Item.class);
        when(item.getShopId()).thenReturn(10L);
        Purchase purchase = Purchase.builder().item(item).paymentId("p-1")
            .user(mock(User.class)).deliveryAddress("Seoul").build();
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(purchase));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doAnswer(invocation -> {
            payment.refund(3_000);
            return null;
        }).when(paymentService).refund("p-1", 3_000, "상품 하자");

        ShopRefundRequest request = new ShopRefundRequest();
        ReflectionTestUtils.setField(request, "paymentId", "p-1");
        ReflectionTestUtils.setField(request, "requestedAmount", 3_000);
        ReflectionTestUtils.setField(request, "reason", "상품 하자");

        RefundResponse.ListDto response = refundService.refundByShop(5L, 10L, request);

        assertEquals(Status.APPROVED, response.getStatus());
        assertEquals(7_000, payment.getRefundableAmount());
        verify(paymentService).refund("p-1", 3_000, "상품 하자");
        verifyNoInteractions(settlementHistoryService);
        // 구매자에게 가게 사유 환불 알림이 가고, 이동 경로는 환불(구매 내역)이다
        verify(publisher).publishEvent(argThat((Object e) -> e instanceof com.goodsbridge.notification.NotificationEvent n
            && n.type() == com.goodsbridge.notification.Notification.Type.PURCHASE_CANCELLED_BY_SHOP
            && n.referenceType() == com.goodsbridge.notification.Notification.ReferenceType.REFUND));
    }

    @Test
    void findAllAdmin_usesStatusAndPageable() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
        when(refundRepository.findAllAdminPage(eq(Status.REQUESTED), any(PageRequest.class)))
            .thenReturn(new PageImpl<>(java.util.List.of()));

        var result = refundService.findAllAdmin(2L, Status.REQUESTED, PageRequest.of(1, 20));

        assertEquals(0, result.getTotalElements());
        verify(refundRepository).findAllAdminPage(eq(Status.REQUESTED), eq(PageRequest.of(1, 20)));
    }

    @Test
    void approveByShop_forbiddenWhenRefundWasRejectedByAdmin() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("파손")
            .requestedAmount(3_000).status(Status.REJECTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        ReflectionTestUtils.setField(refund, "rejectedBy", RejectedBy.ADMIN);
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        Purchase shopSale = shopPurchase(10L, "p-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(shopSale));

        assertThrows(ForbiddenException.class, () -> refundService.approveByShop(5L, 10L, 1L));

        assertEquals(Status.REJECTED, refund.getStatus());
        verifyNoInteractions(paymentService);
    }

    @Test
    void rejectByShop_forbiddenToChangeReasonOfAdminRejectedRefund() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("파손")
            .requestedAmount(3_000).status(Status.REJECTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        ReflectionTestUtils.setField(refund, "rejectReason", "관리자 사유");
        ReflectionTestUtils.setField(refund, "rejectedBy", RejectedBy.ADMIN);
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        Purchase shopSale = shopPurchase(10L, "p-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(shopSale));

        assertThrows(ForbiddenException.class, () -> refundService.rejectByShop(5L, 10L, 1L, "새 사유"));

        assertEquals("관리자 사유", refund.getRejectReason());
    }

    @Test
    void rejectByShop_recordsShopAsRejecter() {
        Shop shop = mock(Shop.class);
        when(shop.isOwner(5L)).thenReturn(true);
        when(shopRepository.findById(10L)).thenReturn(Optional.of(shop));
        Payment payment = Payment.builder().paymentId("p-1").user(mock(User.class)).amount(10_000)
            .purpose(PaymentPurpose.PURCHASE).build();
        Refund refund = Refund.builder().payment(payment).user(mock(User.class)).reason("파손")
            .requestedAmount(3_000).status(Status.REQUESTED).requestedBy(RequestedBy.USER)
            .type(RefundType.PURCHASE).build();
        when(refundRepository.findByIdLock(1L)).thenReturn(Optional.of(refund));
        Purchase shopSale = shopPurchase(10L, "p-1");
        when(purchaseRepository.findByPaymentId("p-1")).thenReturn(Optional.of(shopSale));

        refundService.rejectByShop(5L, 10L, 1L, "상품 이상 없음");

        assertEquals(Status.REJECTED, refund.getStatus());
        assertEquals(RejectedBy.SHOP, refund.getRejectedBy());
    }
}
