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
    @DisplayName("만료일이 지난 구독 대상들을 심플 청크 배치 쿼리로 정상 조회하여 일괄 만료 처리한다")
    void processExpiredSubscriptions_success() {
        // given: 테스트용 사용자 생성
        User owner = userRepository.save(User.builder()
            .email("simple_batch_owner@goodsbridge.com")
            .password("password123!")
            .nickname("청크사장님")
            .loginType(LoginType.EMAIL)
            .build());

        // 가게 생성
        Shop shop = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("청크테스트샵")
            .region("부산")
            .address("해운대구")
            .status(ShopStatus.APPROVED)
            .build());

        Category category = categoryRepository.save(Category.builder().name("생활").build());

        Item item = itemRepository.save(Item.builder()
            .sellerUserId(owner.getId())
            .shopId(shop.getId())
            .category(category)
            .title("청크상품")
            .price(15000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        LocalDate pastDate = LocalDate.now().minusDays(5);

        // 만료된 활성 구독 생성 (배치 쿼리 조건에 정확히 부합)
        ShopSubscription subscription = subscriptionRepository.save(ShopSubscription.builder()
            .shopId(shop.getId())
            .paymentId("PAY_SIMPLE_001")
            .setupFeePaid(true)
            .startDate(pastDate.minusDays(30))
            .endDate(pastDate)
            .status(ShopSubscriptionStatus.ACTIVE)
            .build());

        subscriptionRepository.flush();

        // when: 심플 청크 배치 실행
        int processedCount = subscriptionService.processExpiredSubscriptions();

        // then: 검증
        // 1. 처리된 건수가 정확히 1건이어야 함
        assertThat(processedCount).isEqualTo(1);

        // 2. 구독이 EXPIRED 상태로 바뀌고 소프트 삭제(deletedAt 설정)되었는지 확인
        ShopSubscription processedSub = subscriptionRepository.findDeletedByShopId(shop.getId()).get();
        assertThat(processedSub.getStatus()).isEqualTo(ShopSubscriptionStatus.EXPIRED);
        assertThat(processedSub.getDeletedAt()).isNotNull();

        // 3. 연관 상품이 마감(CLOSED) 처리되었는지 확인
        Item closedItem = itemRepository.findById(item.getId()).get();
        assertThat(closedItem.isDeleted()).isTrue();
        assertThat(closedItem.getStatus()).isEqualTo(ItemStatus.CLOSED);
    }
}
