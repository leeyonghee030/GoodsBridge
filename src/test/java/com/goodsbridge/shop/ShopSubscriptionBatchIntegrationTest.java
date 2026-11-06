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
    @DisplayName("만료일이 지난 활성 구독들을 배치가 순회하며 소프트 삭제 및 연관 상품 판매 마감 처리한다 (Page 0 청크 배치 검증)")
    void processExpiredSubscriptions_success() {
        // given: 테스트용 사용자, 가게, 카테고리, 상품 생성
        User owner = userRepository.save(User.builder()
            .email("batch_test_owner@goodsbridge.com")
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

        Category category = categoryRepository.save(Category.builder().name("디지털/가전").build());

        Item item = itemRepository.save(Item.builder()
            .sellerUserId(owner.getId())
            .shopId(shop.getId())
            .category(category)
            .title("게이밍 키보드")
            .price(45000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        // 과거 날짜로 설정된 만료 구독 데이터 생성 (배치 조회 조건 충족)
        LocalDate pastDate = LocalDate.now().minusDays(5);
        ShopSubscription expiredSubscription = subscriptionRepository.save(ShopSubscription.builder()
            .shopId(shop.getId())
            .paymentId("PAY_BATCH_EXPIRED_001")
            .setupFeePaid(true)
            .startDate(pastDate.minusDays(30))
            .endDate(pastDate) // 오늘보다 이전 (만료됨)
            .status(ShopSubscriptionStatus.ACTIVE)
            .build());

        // when: 심플 청크 배치 서비스 실행 (Page 0 반복 조회 방식)
        int processedCount = subscriptionService.processExpiredSubscriptions();

        // then: 정확히 1건이 처리되었는지 검증
        assertThat(processedCount).isEqualTo(1);

        // 1. 구독 상태 및 소프트 삭제 검증
        ShopSubscription updatedSub = subscriptionRepository.findDeletedByShopId(shop.getId()).get();
        assertThat(updatedSub.getStatus()).isEqualTo(ShopSubscriptionStatus.EXPIRED);
        assertThat(updatedSub.getDeletedAt()).isNotNull();
        assertThat(updatedSub.getPaymentId()).contains("_DELETED_");

        // 2. 연관 상품 마감(CLOSED) 및 소프트 삭제 검증
        Item updatedItem = itemRepository.findById(item.getId()).get();
        assertThat(updatedItem.isDeleted()).isTrue();
        assertThat(updatedItem.getStatus()).isEqualTo(ItemStatus.CLOSED);
    }
}
