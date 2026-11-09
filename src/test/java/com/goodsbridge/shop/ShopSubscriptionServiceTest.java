package com.goodsbridge.shop;

import com.goodsbridge._core.exception.BadRequestException;
import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class ShopSubscriptionServiceTest {

    @Mock
    private ShopSubscriptionRepository subscriptionRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private ShopSubscriptionService subscriptionService;

    @Test
    @DisplayName("일 단위(30일) 신규 구독 생성 성공 테스트")
    void create_new_subscription_success() {
        Long userId = 1L;
        Long shopId = 1L;

        ShopSubscriptionRequest request = createRequest("payment_123", 30000, 30);

        given(subscriptionRepository.existsByPaymentId("payment_123")).willReturn(false);

        Shop shop = mock(Shop.class);
        given(shop.isOwner(userId)).willReturn(true);
        given(shopRepository.findById(shopId)).willReturn(Optional.of(shop));

        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        given(subscriptionRepository.findByShopId(shopId)).willReturn(Optional.empty());

        ShopSubscription savedSubscription = ShopSubscription.builder()
            .shopId(shopId)
            .setupFeePaid(true)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .status(ShopSubscriptionStatus.ACTIVE)
            .paymentId("payment_123")
            .build();
        ReflectionTestUtils.setField(savedSubscription, "id", 100L);

        given(subscriptionRepository.save(any(ShopSubscription.class))).willReturn(savedSubscription);

        Long subscriptionId = subscriptionService.create(userId, shopId, request);

        assertThat(subscriptionId).isEqualTo(100L);
        then(paymentService).should(times(1)).verify(eq("payment_123"), eq(30000), eq(PaymentPurpose.SHOP_SUBSCRIPTION), eq(userId));
        then(subscriptionRepository).should(times(1)).save(any(ShopSubscription.class));
        then(itemRepository).should(times(1)).updateStatusByShopIdAndStatus(eq(shopId), eq(ItemStatus.CLOSED), eq(ItemStatus.ON_SALE));
    }

    @Test
    @DisplayName("활성 상태 구독에 60일 일 단위 기간 누적 연장 성공 테스트")
    void extend_active_subscription_success() {
        Long userId = 1L;
        Long shopId = 1L;

        ShopSubscriptionRequest request = createRequest("payment_456", 60000, 60);

        given(subscriptionRepository.existsByPaymentId("payment_456")).willReturn(false);

        Shop shop = mock(Shop.class);
        given(shop.isOwner(userId)).willReturn(true);
        given(shopRepository.findById(shopId)).willReturn(Optional.of(shop));

        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        LocalDate futureEndDate = LocalDate.now().plusDays(10);
        ShopSubscription existingSubscription = ShopSubscription.builder()
            .shopId(shopId)
            .setupFeePaid(true)
            .startDate(LocalDate.now().minusDays(20))
            .endDate(futureEndDate)
            .status(ShopSubscriptionStatus.ACTIVE)
            .paymentId("old_payment")
            .build();
        ReflectionTestUtils.setField(existingSubscription, "id", 50L);

        given(subscriptionRepository.findByShopId(shopId)).willReturn(Optional.of(existingSubscription));
        given(subscriptionRepository.save(any(ShopSubscription.class))).willReturn(existingSubscription);

        Long subscriptionId = subscriptionService.create(userId, shopId, request);

        assertThat(subscriptionId).isEqualTo(50L);
        assertThat(existingSubscription.getEndDate()).isEqualTo(futureEndDate.plusDays(60));
        then(subscriptionRepository).should(times(1)).save(existingSubscription);
    }

    @Test
    @DisplayName("이미 사용된 paymentId로 구독을 요청하면 BadRequestException이 발생한다")
    void create_fail_duplicate_paymentId() {
        Long userId = 1L;
        Long shopId = 1L;
        String duplicatedPaymentId = "payment_duplicated_123";

        ShopSubscriptionRequest request = createRequest(duplicatedPaymentId, 30000, 30);

        given(subscriptionRepository.existsByPaymentId(duplicatedPaymentId)).willReturn(true);

        assertThatThrownBy(() -> subscriptionService.create(userId, shopId, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessage("이미 처리된 결제 번호(paymentId)입니다. 중복 구독을 진행할 수 없습니다.");

        then(paymentService).should(never()).verify(anyString(), anyInt(), any(), anyLong());
        then(subscriptionRepository).should(never()).save(any(ShopSubscription.class));
    }

    @Test
    @DisplayName("구독 신청 시 선택한 일수(30일)에 비해 결제 금액이 다르면 BadRequestException 발생")
    void create_fail_invalid_amount() {
        Long userId = 1L;
        Long shopId = 1L;

        given(subscriptionRepository.existsByPaymentId("payment_hack")).willReturn(false);

        ShopSubscriptionRequest request = createRequest("payment_hack", 15000, 30);

        assertThatThrownBy(() -> subscriptionService.create(userId, shopId, request))
            .isInstanceOf(BadRequestException.class)
            .hasMessageContaining("결제 금액 정보가 올바르지 않습니다.");
    }

    private ShopSubscriptionRequest createRequest(String paymentId, Integer amount, int days) {
        ShopSubscriptionRequest request = new ShopSubscriptionRequest();
        ReflectionTestUtils.setField(request, "paymentId", paymentId);
        ReflectionTestUtils.setField(request, "amount", amount);
        ReflectionTestUtils.setField(request, "days", days);
        return request;
    }
}
