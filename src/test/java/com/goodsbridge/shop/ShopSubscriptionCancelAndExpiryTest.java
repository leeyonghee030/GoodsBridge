package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge._core.exception.ForbiddenException;
import com.goodsbridge._core.exception.NotFoundException;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.Role;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 관리자 구독 취소: 구독을 CANCELLED 로 바꾸고 취소 이벤트를 발행해 판매중 상품 판매 중지를 리스너에 맡기는지 검증 (DB 없이 Mockito 로)
// 구독 만료 처리는 ShopSubscriptionProcessorTest 가 검증한다
@ExtendWith(MockitoExtension.class)
class ShopSubscriptionCancelAndExpiryTest {

    private static final Long ADMIN_ID = 9L;
    private static final Long SUB_ID = 3L;
    private static final Long SHOP_ID = 5L;

    @Mock ShopSubscriptionRepository subscriptionRepository;
    @Mock ShopRepository shopRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock UserRepository userRepository;
    @Mock PaymentService paymentService;
    @Mock ShopSubscriptionBatchService batchService;
    @InjectMocks ShopSubscriptionService service;

    // ===== 관리자 구독 취소 =====

    @Test
    void cancelByAdmin_marksCancelledAndPublishesCancelEvent() {
        givenAdmin();
        ShopSubscription subscription = subscription(ShopSubscriptionStatus.ACTIVE, LocalDate.now().plusDays(100));
        givenSubscription(subscription);

        service.cancelByAdmin(ADMIN_ID, SUB_ID);

        assertEquals(ShopSubscriptionStatus.CANCELLED, subscription.getStatus());
        ArgumentCaptor<ShopSubscriptionCanceledEvent> event = ArgumentCaptor.forClass(ShopSubscriptionCanceledEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(SHOP_ID, event.getValue().getShopId());
    }

    @Test
    void cancelByAdmin_rejectsAlreadyEndedSubscription() {
        givenAdmin();
        ShopSubscription subscription = subscription(ShopSubscriptionStatus.EXPIRED, LocalDate.now().minusDays(1));
        givenSubscription(subscription);

        assertThrows(BadRequestException.class, () -> service.cancelByAdmin(ADMIN_ID, SUB_ID));
        assertEquals(ShopSubscriptionStatus.EXPIRED, subscription.getStatus());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelByAdmin_rejectsUnknownSubscription() {
        givenAdmin();
        when(subscriptionRepository.findShopIdById(SUB_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.cancelByAdmin(ADMIN_ID, SUB_ID));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void cancelByAdmin_requiresAdmin() {
        User user = mock(User.class);
        when(user.getRole()).thenReturn(Role.USER);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> service.cancelByAdmin(ADMIN_ID, SUB_ID));
        verifyNoInteractions(subscriptionRepository, eventPublisher);
    }

    // ===== 구독 활성 여부 (환불 후 상품 재오픈 판단용) =====

    @Test
    void isSubscriptionActive_checksActiveAndNotExpired() {
        when(subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
            eq(SHOP_ID), eq(ShopSubscriptionStatus.ACTIVE), any(LocalDate.class))).thenReturn(true);
        assertTrue(service.isSubscriptionActive(SHOP_ID));

        when(subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
            eq(SHOP_ID), eq(ShopSubscriptionStatus.ACTIVE), any(LocalDate.class))).thenReturn(false);
        assertFalse(service.isSubscriptionActive(SHOP_ID));
    }

    private void givenAdmin() {
        User admin = mock(User.class);
        when(admin.getRole()).thenReturn(Role.ADMIN);
        when(userRepository.findById(ADMIN_ID)).thenReturn(Optional.of(admin));
    }

    private void givenSubscription(ShopSubscription subscription) {
        when(subscriptionRepository.findShopIdById(SUB_ID)).thenReturn(Optional.of(SHOP_ID));
        when(shopRepository.findByIdWithLock(SHOP_ID)).thenReturn(Optional.of(mock(Shop.class)));
        when(subscriptionRepository.findByIdWithLock(SUB_ID)).thenReturn(Optional.of(subscription));
    }

    private static ShopSubscription subscription(ShopSubscriptionStatus status, LocalDate endDate) {
        return ShopSubscription.builder().id(SUB_ID).shopId(SHOP_ID).paymentId("pay-1").setupFeePaid(true)
            .startDate(endDate.minusDays(30)).endDate(endDate).status(status).build();
    }
}
