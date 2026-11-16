
package com.goodsbridge.shop;

import com.goodsbridge.item.*;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ShopSubscriptionCancelIntegrationTest {

    @Autowired
    private ShopSubscriptionReconciliationService reconciliationService;

    @Autowired
    private ShopSubscriptionRepository subscriptionRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryJpaRepository categoryRepository;

    @AfterEach
    void tearDown() {
        subscriptionRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        shopRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("구독 취소 시 판매 중인 상품만 판매 중지한다")
    void cancelSubscription_pauseOnSaleItems() {

        // 1. 사용자 생성
        User owner = userRepository.save(
            User.builder()
                .email("cancel-test@goodsbridge.com")
                .password("password123!")
                .nickname("취소테스트사장님")
                .loginType(LoginType.EMAIL)
                .build()
        );

        // 2. 가게 생성
        Shop shop = shopRepository.save(
            Shop.builder()
                .ownerUserId(owner.getId())
                .name("구독 취소 테스트샵")
                .region("부산")
                .address("해운대구")
                .status(ShopStatus.APPROVED)
                .build()
        );

        // 3. 카테고리 생성
        Category category = categoryRepository.save(
            Category.builder()
                .name("취소 테스트 카테고리")
                .build()
        );

        // 4. 판매 중인 상품
        Item onSaleItem = itemRepository.save(
            Item.builder()
                .sellerUserId(owner.getId())
                .shopId(shop.getId())
                .category(category)
                .title("판매 중인 상품")
                .price(10000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.ON_SALE)
                .build()
        );

        // 5. 수동 판매 종료 상품
        Item manuallyClosedItem = itemRepository.save(
            Item.builder()
                .sellerUserId(owner.getId())
                .shopId(shop.getId())
                .category(category)
                .title("수동 판매 종료 상품")
                .price(20000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.CLOSED)
                .build()
        );

        // 6. ACTIVE 구독 생성
        LocalDate today = LocalDate.now();

        ShopSubscription subscription = subscriptionRepository.save(
            ShopSubscription.builder()
                .shopId(shop.getId())
                .paymentId("PAY_CANCEL_TEST_001")
                .setupFeePaid(true)
                .startDate(today.minusDays(5))
                .endDate(today.plusDays(30))
                .status(ShopSubscriptionStatus.ACTIVE)
                .build()
        );

        // 7. 구독 취소
        subscription.cancel();

        subscriptionRepository.saveAndFlush(subscription);

        // 8. 구독 취소 후 정합성 복구 실행
        reconciliationService.reconcileShop(shop.getId());

        // 9. DB에서 구독 상태 확인
        ShopSubscription canceledSubscription =
            subscriptionRepository.findById(subscription.getId())
                .orElseThrow();

        assertThat(canceledSubscription.getStatus())
            .isEqualTo(ShopSubscriptionStatus.CANCELLED);

        // 10. DB에서 상품 상태 확인
        Item closedItem = itemRepository.findById(onSaleItem.getId())
            .orElseThrow();

        Item stillClosedItem = itemRepository.findById(manuallyClosedItem.getId())
            .orElseThrow();

        // 판매 중이던 상품은 구독 취소로 판매 중지
        assertThat(closedItem.getStatus())
            .isEqualTo(ItemStatus.CLOSED);

        assertThat(closedItem.isPausedBySubscription())
            .isTrue();

        assertThat(closedItem.isDeleted())
            .isFalse();

        // 수동 판매 종료 상품은 기존 상태 유지
        assertThat(stillClosedItem.getStatus())
            .isEqualTo(ItemStatus.CLOSED);

        assertThat(stillClosedItem.isPausedBySubscription())
            .isFalse();

        assertThat(stillClosedItem.isDeleted())
            .isFalse();
    }
}
