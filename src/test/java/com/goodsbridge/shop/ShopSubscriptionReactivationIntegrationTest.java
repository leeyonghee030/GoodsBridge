
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class ShopSubscriptionReactivationIntegrationTest {

    @Autowired
    private TransactionTemplate transactionTemplate;

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
    @DisplayName("구독 재활성화 시 구독 만료로 중지된 상품만 판매 재개한다")
    void reactivateSubscription_resumeOnlySubscriptionPausedItems() {

        // 1. 사용자 생성
        User owner = userRepository.save(
            User.builder()
                .email("reactivation-test@goodsbridge.com")
                .password("password123!")
                .nickname("재활성화사장님")
                .loginType(LoginType.EMAIL)
                .build()
        );

        // 2. 승인된 가게 생성
        Shop shop = shopRepository.save(
            Shop.builder()
                .ownerUserId(owner.getId())
                .name("재활성화 테스트샵")
                .region("부산")
                .address("해운대구")
                .status(ShopStatus.APPROVED)
                .build()
        );

        // 3. 카테고리 생성
        Category category = categoryRepository.save(
            Category.builder()
                .name("재활성화 테스트 카테고리")
                .build()
        );

        // 4. 구독 만료 때문에 중지된 상품
        // 구독 만료로 중지된 상품
        Item pausedItem = itemRepository.save(
            Item.builder()
                .sellerUserId(owner.getId())
                .shopId(shop.getId())
                .category(category)
                .title("구독 만료로 중지된 상품")
                .price(10000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.ON_SALE)
                .build()
        );


        pausedItem = itemRepository.save(pausedItem);

        // 5. 관리자가 직접 판매 종료한 상품
        Item manuallyClosedItem = itemRepository.save(
            Item.builder()
                .sellerUserId(owner.getId())
                .shopId(shop.getId())
                .category(category)
                .title("수동으로 판매 종료한 상품")
                .price(20000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.CLOSED)
                .build()
        );

        // 6. 구독 만료로 상품이 중지된 상태 만들기
        Integer pausedCount = transactionTemplate.execute(status ->
            itemRepository.pauseItemsBySubscription(
                shop.getId(),
                ItemStatus.ON_SALE,
                ItemStatus.CLOSED
            )
        );

        assertThat(pausedCount).isEqualTo(1);

// 7. 구독을 ACTIVE 상태로 준비
        LocalDate today = LocalDate.now();

        subscriptionRepository.save(
            ShopSubscription.builder()
                .shopId(shop.getId())
                .paymentId("PAY_REACTIVATION_TEST_001")
                .setupFeePaid(true)
                .startDate(today.minusDays(5))
                .endDate(today.plusDays(30))
                .status(ShopSubscriptionStatus.ACTIVE)
                .build()
        );

// 8. 구독 정합성 복구 실행
        reconciliationService.reconcileShop(shop.getId());

// 9. DB에서 상품을 다시 조회하여 검증
    }
}
