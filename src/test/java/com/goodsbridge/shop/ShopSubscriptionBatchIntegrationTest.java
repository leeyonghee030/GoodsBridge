
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
class ShopSubscriptionBatchIntegrationTest {

    @Autowired
    private ShopSubscriptionBatchService batchService;

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

    // 테스트 종료 후 데이터 정리
    @AfterEach
    void tearDown() {
        subscriptionRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        shopRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("ID 커서 페이징 배치로 만료된 구독을 정확하게 처리하고 연관 상품 판매를 중지한다")
    void processExpiredSubscriptions_cursorPaging_success() {

        // given

        // 1. 테스트 사용자 생성
        User owner = userRepository.save(
            User.builder()
                .email("cursor_batch_owner@goodsbridge.com")
                .password("password123!")
                .nickname("커서사장님")
                .loginType(LoginType.EMAIL)
                .build()
        );

        // 2. 가게 2개 생성
        Shop shopA = shopRepository.save(
            Shop.builder()
                .ownerUserId(owner.getId())
                .name("커서샵A")
                .region("부산")
                .address("해운대구")
                .status(ShopStatus.APPROVED)
                .build()
        );

        Shop shopB = shopRepository.save(
            Shop.builder()
                .ownerUserId(owner.getId())
                .name("커서샵B")
                .region("부산")
                .address("수영구")
                .status(ShopStatus.APPROVED)
                .build()
        );

        // 3. 카테고리 생성
        Category category = categoryRepository.save(
            Category.builder()
                .name("패션")
                .build()
        );

        // 4. 각 가게의 판매 중인 상품 생성
        Item itemA = itemRepository.save(
            Item.builder()
                .sellerUserId(owner.getId())
                .shopId(shopA.getId())
                .category(category)
                .title("커서상품A")
                .price(10000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.ON_SALE)
                .build()
        );

        Item itemB = itemRepository.save(
            Item.builder()
                .sellerUserId(owner.getId())
                .shopId(shopB.getId())
                .category(category)
                .title("커서상품B")
                .price(20000)
                .saleType(SaleType.NORMAL)
                .status(ItemStatus.ON_SALE)
                .build()
        );

        // 5. 과거 날짜 설정
        LocalDate pastDate = LocalDate.now().minusDays(5);

        // 6. 만료된 ACTIVE 구독 생성
        ShopSubscription subA = subscriptionRepository.save(
            ShopSubscription.builder()
                .shopId(shopA.getId())
                .paymentId("PAY_CURSOR_001")
                .setupFeePaid(true)
                .startDate(pastDate.minusDays(30))
                .endDate(pastDate)
                .status(ShopSubscriptionStatus.ACTIVE)
                .build()
        );

        ShopSubscription subB = subscriptionRepository.save(
            ShopSubscription.builder()
                .shopId(shopB.getId())
                .paymentId("PAY_CURSOR_002")
                .setupFeePaid(true)
                .startDate(pastDate.minusDays(30))
                .endDate(pastDate)
                .status(ShopSubscriptionStatus.ACTIVE)
                .build()
        );

        // when

        // 분리된 배치 서비스 실행
        int processedCount = batchService.processExpiredSubscriptions();

        // then

        // 1. 만료 구독 2건이 처리되어야 한다.
        assertThat(processedCount).isEqualTo(2);

        // 2. 구독 A가 EXPIRED 상태로 변경되었는지 확인
        ShopSubscription processedSubA =
            subscriptionRepository.findById(subA.getId())
                .orElseThrow();

        assertThat(processedSubA.getStatus())
            .isEqualTo(ShopSubscriptionStatus.EXPIRED);

        // 구독 이력은 보존되어야 한다.
        assertThat(processedSubA.getDeletedAt()).isNull();

        // 3. 구독 B가 EXPIRED 상태로 변경되었는지 확인
        ShopSubscription processedSubB =
            subscriptionRepository.findById(subB.getId())
                .orElseThrow();

        assertThat(processedSubB.getStatus())
            .isEqualTo(ShopSubscriptionStatus.EXPIRED);

        // 구독 이력은 보존되어야 한다.
        assertThat(processedSubB.getDeletedAt()).isNull();

        // 4. 상품 A의 판매 상태 확인
        Item closedItemA =
            itemRepository.findById(itemA.getId())
                .orElseThrow();

        assertThat(closedItemA.getStatus())
            .isEqualTo(ItemStatus.CLOSED);

        // 구독 만료로 인한 판매 중지이므로 상품 자체는 삭제하지 않는다.
        assertThat(closedItemA.isDeleted()).isFalse();

        // 5. 상품 B의 판매 상태 확인
        Item closedItemB =
            itemRepository.findById(itemB.getId())
                .orElseThrow();

        assertThat(closedItemB.getStatus())
            .isEqualTo(ItemStatus.CLOSED);

        assertThat(closedItemB.isDeleted()).isFalse();
    }
}
