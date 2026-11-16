package com.goodsbridge.shop;

import com.goodsbridge.item.*;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ShopServiceIntegrationTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ShopSubscriptionRepository subscriptionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryJpaRepository categoryRepository;

    @Test
    @DisplayName("가게 삭제 시 연관된 상품과 활성 구독이 소프트 삭제되고 구독 상태는 CANCELLED로 변경된다.")
    void deleteShop_cascadesSoftDeleteToItemsAndSubscription() {
        // given
        User owner = userRepository.save(User.builder()
            .email("owner@goodsbridge.com")
            .password("password123!")
            .nickname("사장님")
            .loginType(LoginType.EMAIL)
            .build());

        Shop shop = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("테스트 굿즈샵")
            .region("부산")
            .address("해운대구 우동")
            .status(ShopStatus.APPROVED)
            .build());

        Category category = categoryRepository.save(Category.builder()
            .name("피규어")
            .build());

        Item item = itemRepository.save(Item.builder()
            .sellerUserId(owner.getId())
            .shopId(shop.getId())
            .category(category)
            .title("한정판 피규어")
            .price(50000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        ShopSubscription subscription = subscriptionRepository.save(
            ShopSubscription.builder()
                .shopId(shop.getId())
                .paymentId("PAY_TEST_12345")
                .setupFeePaid(true)
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(25))
                .status(ShopSubscriptionStatus.ACTIVE)
                .build()
        );

        // when
        shopService.delete(owner.getId(), shop.getId());

        // then

        // 1. 가게 삭제 검증
        Shop deletedShop = shopRepository.findById(shop.getId()).get();

        assertThat(deletedShop.isDeleted()).isTrue();
        assertThat(deletedShop.getStatus()).isEqualTo(ShopStatus.CLOSED);

        // 2. 상품 소프트 삭제 검증
        Item deletedItem = itemRepository.findById(item.getId()).get();

        assertThat(deletedItem.isDeleted()).isTrue();
        assertThat(deletedItem.getStatus()).isEqualTo(ItemStatus.CLOSED);

        // 3. 구독 소프트 삭제 및 취소 상태 검증
        ShopSubscription deletedSubscription =
            subscriptionRepository.findDeletedByShopId(shop.getId()).get();

        assertThat(deletedSubscription.getStatus())
            .isEqualTo(ShopSubscriptionStatus.CANCELLED);

        assertThat(deletedSubscription.getDeletedAt()).isNotNull();

        // 4. 결제 ID는 변경되지 않아야 한다.
        assertThat(deletedSubscription.getPaymentId())
            .isEqualTo("PAY_TEST_12345");
    }
}
