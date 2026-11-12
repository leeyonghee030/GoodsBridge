package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 관리자 구독 취소와 구독 만료 처리가 "판매중 상품만" 닫는지 검증한다 (DB 없이 Mockito 로)
@ExtendWith(MockitoExtension.class)
class ShopSubscriptionCancelAndExpiryTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long SUB_ID = 3L;
    private static final Long SHOP_ID = 5L;

    @Mock ShopSubscriptionRepository subscriptionRepository;
    @Mock ShopRepository shopRepository;
    @Mock ItemRepository itemRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentService paymentService;
    @Mock ShopSubscriptionProcessor subscriptionProcessor;
    @InjectMocks ShopSubscriptionService service;

    // ===== 관리자 구독 취소 =====

    @Test
    void cancelByAdmin_marksCancelledAndClosesOnlyOnSaleItems() {
        givenAdmin();
        ShopSubscription subscription = subscription(ShopSubscriptionStatus.ACTIVE, LocalDate.now().plusDays(100));
        when(subscriptionRepository.findByIdWithLock(SUB_ID)).thenReturn(Optional.of(subscription));

        service.cancelByAdmin(ADMIN_ID, SUB_ID);

        assertEquals(ShopSubscriptionStatus.CANCELLED, subscription.getStatus());
        verify(itemRepository).closeOnSaleByShopId(eq(SHOP_ID), any(LocalDateTime.class));
        verify(itemRepository, never()).softDeleteByShopId(any(), any());
    }

    @Test
    void cancelByAdmin_rejectsAlreadyEndedSubscription() {
        givenAdmin();
        ShopSubscription subscription = subscription(ShopSubscriptionStatus.EXPIRED, LocalDate.now().minusDays(1));
        when(subscriptionRepository.findByIdWithLock(SUB_ID)).thenReturn(Optional.of(subscription));

        assertThrows(BadRequestException.class, () -> service.cancelByAdmin(ADMIN_ID, SUB_ID));
        assertEquals(ShopSubscriptionStatus.EXPIRED, subscription.getStatus());
        verifyNoInteractions(itemRepository);
    }

    @Test
    void cancelByAdmin_rejectsUnknownSubscription() {
        givenAdmin();
        when(subscriptionRepository.findByIdWithLock(SUB_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.cancelByAdmin(ADMIN_ID, SUB_ID));
    }

    @Test
    void cancelByAdmin_requiresAdmin() {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(Role.USER);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> service.cancelByAdmin(ADMIN_ID, SUB_ID));
        verifyNoInteractions(itemRepository);
    }

    // ===== 취소/만료된 구독은 상품 등록이 막힌다 (isSubscriptionActive) =====

    @Test
    void isSubscriptionActive_falseWhenCancelledOrExpiredByDate() {
        when(subscriptionRepository.findByShopId(SHOP_ID))
            .thenReturn(Optional.of(subscription(ShopSubscriptionStatus.CANCELLED, LocalDate.now().plusDays(30))));
        assertFalse(service.isSubscriptionActive(SHOP_ID));

        when(subscriptionRepository.findByShopId(SHOP_ID))
            .thenReturn(Optional.of(subscription(ShopSubscriptionStatus.ACTIVE, LocalDate.now().minusDays(1))));
        assertFalse(service.isSubscriptionActive(SHOP_ID));

        when(subscriptionRepository.findByShopId(SHOP_ID))
            .thenReturn(Optional.of(subscription(ShopSubscriptionStatus.ACTIVE, LocalDate.now())));
        assertTrue(service.isSubscriptionActive(SHOP_ID));   // 만료 당일까지는 유효
    }

    // ===== 구독 만료 처리: 판매중 상품만 닫는다 =====

    @Test
    void expiry_closesOnlyOnSaleItemsAndKeepsSoldOnes() {
        ShopSubscriptionProcessor processor = new ShopSubscriptionProcessor(itemRepository, subscriptionRepository);
        ShopSubscription subscription = subscription(ShopSubscriptionStatus.ACTIVE, LocalDate.now().minusDays(1));
        when(subscriptionRepository.findByIdWithLock(SUB_ID)).thenReturn(Optional.of(subscription));

        boolean processed = processor.processSingleSubscriptionById(SUB_ID);

        assertTrue(processed);
        assertEquals(ShopSubscriptionStatus.EXPIRED, subscription.getStatus());
        verify(itemRepository).closeOnSaleByShopId(eq(SHOP_ID), any(LocalDateTime.class));
        verify(itemRepository, never()).softDeleteByShopId(any(), any());
    }

    @Test
    void expiry_skipsSubscriptionThatWasExtendedMeanwhile() {
        ShopSubscriptionProcessor processor = new ShopSubscriptionProcessor(itemRepository, subscriptionRepository);
        ShopSubscription subscription = subscription(ShopSubscriptionStatus.ACTIVE, LocalDate.now().plusDays(10));
        when(subscriptionRepository.findByIdWithLock(SUB_ID)).thenReturn(Optional.of(subscription));

        assertFalse(processor.processSingleSubscriptionById(SUB_ID));
        verifyNoInteractions(itemRepository);
    }

    private void givenAdmin() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
    }

    private static ShopSubscription subscription(ShopSubscriptionStatus status, LocalDate endDate) {
        return ShopSubscription.builder().id(SUB_ID).shopId(SHOP_ID).paymentId("pay-1").setupFeePaid(true)
            .startDate(endDate.minusDays(30)).endDate(endDate).status(status).build();
    }
}
