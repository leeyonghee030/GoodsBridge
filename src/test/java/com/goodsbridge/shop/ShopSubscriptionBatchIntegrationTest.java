package com.goodsbridge.shop;

import com.goodsbridge.item.*;
import com.goodsbridge.user.LoginType;
import com.goodsbridge.user.User;
import com.goodsbridge.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ShopSubscriptionBatchIntegrationTest {

    @Autowired
    private ShopSubscriptionService subscriptionService;

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

    @Test
    @DisplayName("만료일이 지난 활성 구독들을 배치가 순회하며 소프트 삭제 및 연관 상품 판매 마감 처리한다.")
    void processExpiredSubscriptions_success() {
        // given
        User owner = userRepository.save(User.builder()
            .email("batch_owner@goodsbridge.com")
            .password("password123!")
            .nickname("배치사장님")
            .loginType(LoginType.EMAIL)
            .build());

        Shop shop = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("만료테스트샵")
            .region("부산")
            .address("부산진구 부전동")
            .status(ShopStatus.APPROVED)
            .build());

        Category category = categoryRepository.save(Category.builder().name("디지털").build());

        Item item = itemRepository.save(Item.builder()
            .sellerUserId(owner.getId())
            .shopId(shop.getId())
            .category(category)
            .title("무선 키보드")
            .price(35000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        // 확실하게 과거 날짜로 설정 (배치 쿼리가 인식할 수 있도록 며칠 전으로 지정)
        LocalDate pastDate = LocalDate.now().minusDays(5);
        ShopSubscription expiredSubscription = subscriptionRepository.save(ShopSubscription.builder()
            .shopId(shop.getId())
            .paymentId("PAY_EXPIRED_999")
            .setupFeePaid(true)
            .startDate(pastDate.minusDays(30))
            .endDate(pastDate) // 명확히 과거 날짜
            .status(ShopSubscriptionStatus.ACTIVE)
            .build());

        // when: 배치 실행 전 플러시를 통해 DB에 쿼리를 즉시 반영하여 락 충돌 방지
        int processedCount = subscriptionService.processExpiredSubscriptions();

        // then
        assertThat(processedCount).isEqualTo(1);

        ShopSubscription updatedSub = subscriptionRepository.findDeletedByShopId(shop.getId()).get();
        assertThat(updatedSub.getStatus()).isEqualTo(ShopSubscriptionStatus.EXPIRED);
        assertThat(updatedSub.getDeletedAt()).isNotNull();
        assertThat(updatedSub.getPaymentId()).contains("_DELETED_");

        Item updatedItem = itemRepository.findById(item.getId()).get();
        assertThat(updatedItem.isDeleted()).isTrue();
        assertThat(updatedItem.getStatus()).isEqualTo(ItemStatus.CLOSED);
    }
}
