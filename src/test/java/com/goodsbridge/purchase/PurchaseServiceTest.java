package com.goodsbridge.purchase;

import com.goodsbridge.item.Item;
import com.goodsbridge.item.ItemRepository;
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
        verifyNoInteractions(paymentService);
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
        verifyNoInteractions(paymentService);
    }

    @Test
    void purchase_convertsUniqueViolationOnSaveToBadRequest() {
        Item item = mock(Item.class);
        when(item.getSellerUserId()).thenReturn(99L);
        when(item.getPrice()).thenReturn(10_000);
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
    }

    @Test
    void confirm_isIdempotentWhenAlreadyConfirmed() {
        User buyer = mock(User.class);
        when(buyer.getId()).thenReturn(10L);
        Purchase purchase = Purchase.builder()
            .item(mock(Item.class)).user(buyer).paymentId("payment-1")
            .deliveryAddress("Seoul").build();
        purchase.ship("TRACK-1");
        purchase.confirm();
        when(purchaseRepository.findById(1L)).thenReturn(java.util.Optional.of(purchase));

        purchaseService.confirm(1L, 10L);

        verifyNoInteractions(paymentService, settlementHistoryService);
    }
}
