package com.goodsbridge.shop;

import com.goodsbridge.item.ItemRepository;
import com.goodsbridge.item.ItemStatus;
import com.goodsbridge.payment.PaymentPurpose;
import com.goodsbridge.payment.PaymentService;
import com.goodsbridge.user.Role;
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
    @DisplayName("신규 구독 생성 성공 테스트 (기존 구독 이력이 없는 경우)")
    void create_new_subscription_success() {
        // given
        Long userId = 1L;
        Long shopId = 1L;

        ShopSubscriptionRequest request = createRequest("payment_123", 30000, 30);

        Shop shop = mock(Shop.class);
        given(shop.isOwner(userId)).willReturn(true);
        given(shopRepository.findById(shopId)).willReturn(Optional.of(shop));

        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // 기존 구독 이력 없음
        given(subscriptionRepository.findByShopId(shopId)).willReturn(Optional.empty());

        // 저장 시 모킹
        ShopSubscription savedSubscription = ShopSubscription.builder()
            .shopId(shopId)
            .setupFeePaid(true)
            .startDate(LocalDate.now())
            .endDate(LocalDate.now().plusDays(30))
            .status(ShopSubscriptionStatus.ACTIVE)
            .build();
        ReflectionTestUtils.setField(savedSubscription, "id", 100L);

        given(subscriptionRepository.save(any(ShopSubscription.class))).willReturn(savedSubscription);

        // when
        Long subscriptionId = subscriptionService.create(userId, shopId, request);

        // then
        assertThat(subscriptionId).isEqualTo(100L);
        then(paymentService).should(times(1)).verify(eq("payment_123"), eq(30000), eq(PaymentPurpose.SHOP_SUBSCRIPTION), eq(userId));
        then(subscriptionRepository).should(times(1)).save(any(ShopSubscription.class));
        // 신규/만료 가입 시 CLOSED 상품 복구 로직 실행 확인
        then(itemRepository).should(times(1)).updateStatusByShopIdAndStatus(eq(shopId), eq(ItemStatus.CLOSED), eq(ItemStatus.ON_SALE));
    }

    @Test
    @DisplayName("활성 상태 구독 연장 성공 테스트 (기간 누적)")
    void extend_active_subscription_success() {
        // given
        Long userId = 1L;
        Long shopId = 1L;

        ShopSubscriptionRequest request = createRequest("payment_456", 30000, 30);

        Shop shop = mock(Shop.class);
        given(shop.isOwner(userId)).willReturn(true);
        given(shopRepository.findById(shopId)).willReturn(Optional.of(shop));

        User user = mock(User.class);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // 이미 미래까지 유효한 ACTIVE 상태의 기존 구독 존재
        LocalDate futureEndDate = LocalDate.now().plusDays(10);
        ShopSubscription existingSubscription = ShopSubscription.builder()
            .shopId(shopId)
            .setupFeePaid(true)
            .startDate(LocalDate.now().minusDays(20))
            .endDate(futureEndDate)
            .status(ShopSubscriptionStatus.ACTIVE)
            .build();
        ReflectionTestUtils.setField(existingSubscription, "id", 50L);

        given(subscriptionRepository.findByShopId(shopId)).willReturn(Optional.of(existingSubscription));
        given(subscriptionRepository.save(any(ShopSubscription.class))).willReturn(existingSubscription);

        // when
        Long subscriptionId = subscriptionService.create(userId, shopId, request);

        // then
        assertThat(subscriptionId).isEqualTo(50L);
        // 기존 종료일(futureEndDate)에서 30일이 연장되었는지 검증
        assertThat(existingSubscription.getEndDate()).isEqualTo(futureEndDate.plusDays(30));
        then(subscriptionRepository).should(times(1)).save(existingSubscription);
    }

    // 테스트용 Request 객체 생성 헬퍼 메서드 (setter가 없는 DTO 대응)
    private ShopSubscriptionRequest createRequest(String paymentId, Integer amount, int days) {
        ShopSubscriptionRequest request = new ShopSubscriptionRequest();
        ReflectionTestUtils.setField(request, "paymentId", paymentId);
        ReflectionTestUtils.setField(request, "amount", amount);
        ReflectionTestUtils.setField(request, "days", days);
        return request;
    }
}
