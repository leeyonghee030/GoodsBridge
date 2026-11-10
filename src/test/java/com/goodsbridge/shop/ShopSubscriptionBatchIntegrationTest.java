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
@SpringBootTest // 💡 @Transactional 제거!
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

    // 테스트 종료 후 데이터 정리 (필요에 따라 작성)
    @AfterEach
    void tearDown() {
        subscriptionRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        shopRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("ID 커서 페이징 배치를 통해 만료된 구독들을 누락 및 무한 루프 없이 정확하게 일괄 만료 처리한다")
    void processExpiredSubscriptions_cursorPaging_success() {
        // given: 테스트용 사용자 생성
        User owner = userRepository.save(User.builder()
            .email("cursor_batch_owner@goodsbridge.com")
            .password("password123!")
            .nickname("커서사장님")
            .loginType(LoginType.EMAIL)
            .build());

        // 가게 2개 생성
        Shop shopA = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("커서샵A")
            .region("부산")
            .address("해운대구")
            .status(ShopStatus.APPROVED)
            .build());

        Shop shopB = shopRepository.save(Shop.builder()
            .ownerUserId(owner.getId())
            .name("커서샵B")
            .region("부산")
            .address("수영구")
            .status(ShopStatus.APPROVED)
            .build());

        Category category = categoryRepository.save(Category.builder().name("패션").build());

        // 각 가게의 상품 생성
        Item itemA = itemRepository.save(Item.builder()
            .sellerUserId(owner.getId())
            .shopId(shopA.getId())
            .category(category)
            .title("커서상품A")
            .price(10000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        Item itemB = itemRepository.save(Item.builder()
            .sellerUserId(owner.getId())
            .shopId(shopB.getId())
            .category(category)
            .title("커서상품B")
            .price(20000)
            .saleType(SaleType.NORMAL)
            .status(ItemStatus.ON_SALE)
            .build());

        LocalDate pastDate = LocalDate.now().minusDays(5);

        // 만료된 활성 구독 2개 생성
        ShopSubscription subA = subscriptionRepository.save(ShopSubscription.builder()
            .shopId(shopA.getId())
            .paymentId("PAY_CURSOR_001")
            .setupFeePaid(true)
            .startDate(pastDate.minusDays(30))
            .endDate(pastDate)
            .status(ShopSubscriptionStatus.ACTIVE)
            .build());

        ShopSubscription subB = subscriptionRepository.save(ShopSubscription.builder()
            .shopId(shopB.getId())
            .paymentId("PAY_CURSOR_002")
            .setupFeePaid(true)
            .startDate(pastDate.minusDays(30))
            .endDate(pastDate)
            .status(ShopSubscriptionStatus.ACTIVE)
            .build());

        // when: ID 커서 페이징 기반 배치 서비스 실행 (독립 커넥션에서 바로 커밋된 데이터를 완벽하게 조회함)
        int processedCount = subscriptionService.processExpiredSubscriptions();

        // then: 검증
        // 1. 처리된 총 건수가 정확히 2건이어야 함
        assertThat(processedCount).isEqualTo(2);

        // 2. 구독 A, B 모두 EXPIRED 상태로 바뀌고 소프트 삭제(deletedAt 설정)되었는지 확인
        ShopSubscription processedSubA = subscriptionRepository.findDeletedByShopId(shopA.getId()).get();
        assertThat(processedSubA.getStatus()).isEqualTo(ShopSubscriptionStatus.EXPIRED);
        assertThat(processedSubA.getDeletedAt()).isNotNull();

        ShopSubscription processedSubB = subscriptionRepository.findDeletedByShopId(shopB.getId()).get();
        assertThat(processedSubB.getStatus()).isEqualTo(ShopSubscriptionStatus.EXPIRED);
        assertThat(processedSubB.getDeletedAt()).isNotNull();

        // 3. 연관 상품 A, B 모두 마감(CLOSED) 처리되었는지 확인
        Item closedItemA = itemRepository.findById(itemA.getId()).get();
        assertThat(closedItemA.isDeleted()).isTrue();
        assertThat(closedItemA.getStatus()).isEqualTo(ItemStatus.CLOSED);

        Item closedItemB = itemRepository.findById(itemB.getId()).get();
        assertThat(closedItemB.isDeleted()).isTrue();
        assertThat(closedItemB.getStatus()).isEqualTo(ItemStatus.CLOSED);
    }
}
