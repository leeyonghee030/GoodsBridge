
package com.goodsbridge.shop;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class ShopSubscriptionRepositoryTest {


    @PersistenceContext
    private EntityManager em;
    @Autowired
    private ShopSubscriptionRepository subscriptionRepository;

    @Test
    @DisplayName("ACTIVE 구독의 종료일이 오늘이면 활성 구독으로 조회된다.")
    void existsByShopIdAndStatusAndEndDateGreaterThanEqual_today_test() {
        // given
        Long shopId = 100L;

        ShopSubscription subscription = createSubscription(
            shopId,
            LocalDate.now(),
            ShopSubscriptionStatus.ACTIVE
        );

        subscriptionRepository.save(subscription);

        // when
        boolean result =
            subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ACTIVE 구독의 종료일이 미래이면 활성 구독으로 조회된다.")
    void existsByShopIdAndStatusAndEndDateGreaterThanEqual_future_test() {
        // given
        Long shopId = 101L;

        ShopSubscription subscription = createSubscription(
            shopId,
            LocalDate.now().plusDays(30),
            ShopSubscriptionStatus.ACTIVE
        );

        subscriptionRepository.save(subscription);

        // when
        boolean result =
            subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("ACTIVE 구독이어도 종료일이 과거이면 활성 구독으로 조회되지 않는다.")
    void existsByShopIdAndStatusAndEndDateGreaterThanEqual_expired_test() {
        // given
        Long shopId = 102L;

        ShopSubscription subscription = createSubscription(
            shopId,
            LocalDate.now().minusDays(1),
            ShopSubscriptionStatus.ACTIVE
        );

        subscriptionRepository.save(subscription);

        // when
        boolean result =
            subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("구독 이력이 없는 가게는 활성 구독으로 조회되지 않는다.")
    void existsByShopIdAndStatusAndEndDateGreaterThanEqual_noHistory_test() {
        // given
        Long shopId = 103L;

        // when
        boolean result =
            subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("구독 상태가 EXPIRED이면 종료일이 미래여도 활성 구독으로 조회되지 않는다.")
    void existsByShopIdAndStatusAndEndDateGreaterThanEqual_expiredStatus_test() {
        // given
        Long shopId = 104L;

        ShopSubscription subscription = createSubscription(
            shopId,
            LocalDate.now().plusDays(30),
            ShopSubscriptionStatus.EXPIRED
        );

        subscriptionRepository.save(subscription);

        // when
        boolean result =
            subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );

        // then
        assertThat(result).isFalse();
    }

    private ShopSubscription createSubscription(
        Long shopId,
        LocalDate endDate,
        ShopSubscriptionStatus status
    ) {
        return ShopSubscription.builder()
            .shopId(shopId)
            .paymentId("PAY_TEST_" + UUID.randomUUID())
            .setupFeePaid(true)
            .startDate(LocalDate.now().minusDays(30))
            .endDate(endDate)
            .status(status)
            .build();
    }
    @Test
    @DisplayName("소프트 삭제된 구독은 활성 구독으로 조회되지 않는다.")
    void existsByShopIdAndStatusAndEndDateGreaterThanEqual_deleted_test() {
        // given
        Long shopId = 105L;

        ShopSubscription subscription = createSubscription(
            shopId,
            LocalDate.now().plusDays(30),
            ShopSubscriptionStatus.ACTIVE
        );

        subscription.delete();
        subscriptionRepository.save(subscription);

        // when
        boolean result =
            subscriptionRepository.existsByShopIdAndStatusAndEndDateGreaterThanEqual(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );

        // then
        assertThat(result).isFalse();
    }
    @Test
    @DisplayName("동일한 가게의 구독 이력이 여러 개이면 가장 최근 구독을 조회한다.")
    void findFirstByShopIdOrderByIdDesc_test() {
        // given
        Long shopId = 106L;

        ShopSubscription firstSubscription = createSubscription(
            shopId,
            LocalDate.now().plusDays(10),
            ShopSubscriptionStatus.EXPIRED
        );

        ShopSubscription secondSubscription = ShopSubscription.builder()
            .shopId(shopId)
            .paymentId("PAY_TEST_106_SECOND")
            .setupFeePaid(true)
            .startDate(LocalDate.now().minusDays(30))
            .endDate(LocalDate.now().plusDays(30))
            .status(ShopSubscriptionStatus.ACTIVE)
            .build();

        subscriptionRepository.save(firstSubscription);
        subscriptionRepository.save(secondSubscription);

        // when
        ShopSubscription result = subscriptionRepository
            .findFirstByShopIdOrderByIdDesc(shopId)
            .orElseThrow();

        // then
        assertThat(result.getId()).isEqualTo(secondSubscription.getId());
        assertThat(result.getId()).isGreaterThan(firstSubscription.getId());
        assertThat(result.getStatus()).isEqualTo(ShopSubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("만료된 ACTIVE 구독만 조회하고 EXPIRED 및 소프트 삭제 구독은 제외한다.")
    void findExpiredActiveSubscriptionIdsAfterId_filter_test() {
        // given
        LocalDate today = LocalDate.now();

        ShopSubscription expiredActive = createSubscription(
            201L, today.minusDays(1), ShopSubscriptionStatus.ACTIVE
        );

        ShopSubscription todayActive = createSubscription(
            202L, today, ShopSubscriptionStatus.ACTIVE
        );

        ShopSubscription expiredStatus = createSubscription(
            203L, today.minusDays(10), ShopSubscriptionStatus.EXPIRED
        );

        ShopSubscription deletedActive = createSubscription(
            204L, today.minusDays(5), ShopSubscriptionStatus.ACTIVE
        );
        deletedActive.delete();

        subscriptionRepository.save(expiredActive);
        subscriptionRepository.save(todayActive);
        subscriptionRepository.save(expiredStatus);
        subscriptionRepository.save(deletedActive);

        // when
        List<Long> result =
            subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
                0L,
                today,
                ShopSubscriptionStatus.ACTIVE,
                PageRequest.of(0, 10)
            );

        // then
        assertThat(result)
            .containsExactly(expiredActive.getId());
    }

    @Test
    @DisplayName("lastId보다 큰 ID를 가진 만료 구독만 조회한다.")
    void findExpiredActiveSubscriptionIdsAfterId_cursor_test() {
        // given
        LocalDate today = LocalDate.now();

        ShopSubscription first = subscriptionRepository.save(
            createSubscription(211L, today.minusDays(3), ShopSubscriptionStatus.ACTIVE)
        );

        ShopSubscription second = subscriptionRepository.save(
            createSubscription(212L, today.minusDays(2), ShopSubscriptionStatus.ACTIVE)
        );

        ShopSubscription third = subscriptionRepository.save(
            createSubscription(213L, today.minusDays(1), ShopSubscriptionStatus.ACTIVE)
        );

        // when
        List<Long> result =
            subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
                second.getId(),
                today,
                ShopSubscriptionStatus.ACTIVE,
                PageRequest.of(0, 10)
            );

        // then
        assertThat(result).containsExactly(third.getId());
        assertThat(result).doesNotContain(first.getId(), second.getId());
    }

    @Test
    @DisplayName("만료 구독 ID를 오름차순으로 정렬하고 페이지 크기만큼 조회한다.")
    void findExpiredActiveSubscriptionIdsAfterId_paging_test() {
        // given
        LocalDate today = LocalDate.now();

        ShopSubscription first = subscriptionRepository.save(
            createSubscription(221L, today.minusDays(3), ShopSubscriptionStatus.ACTIVE)
        );

        ShopSubscription second = subscriptionRepository.save(
            createSubscription(222L, today.minusDays(2), ShopSubscriptionStatus.ACTIVE)
        );

        ShopSubscription third = subscriptionRepository.save(
            createSubscription(223L, today.minusDays(1), ShopSubscriptionStatus.ACTIVE)
        );

        // when
        List<Long> result =
            subscriptionRepository.findExpiredActiveSubscriptionIdsAfterId(
                0L,
                today,
                ShopSubscriptionStatus.ACTIVE,
                PageRequest.of(0, 2)
            );

        // then
        assertThat(result)
            .containsExactly(first.getId(), second.getId());

        assertThat(result).doesNotContain(third.getId());
    }

    @Test
    @DisplayName("구독 ID로 조회하면 해당 구독 정보를 반환한다.")
    void findByIdWithLock_success_test() {
        // given
        ShopSubscription subscription = subscriptionRepository.save(
            createSubscription(
                301L,
                LocalDate.now().plusDays(30),
                ShopSubscriptionStatus.ACTIVE
            )
        );

        Long subscriptionId = subscription.getId();

        em.flush();
        em.clear();

        // when
        ShopSubscription result = subscriptionRepository
            .findByIdWithLock(subscriptionId)
            .orElseThrow();

        // then
        assertThat(result.getId()).isEqualTo(subscriptionId);
        assertThat(result.getShopId()).isEqualTo(301L);
        assertThat(result.getStatus()).isEqualTo(ShopSubscriptionStatus.ACTIVE);
    }

    @Test
    @DisplayName("존재하지 않는 구독 ID로 조회하면 빈 Optional을 반환한다.")
    void findByIdWithLock_notFound_test() {
        // given
        Long notExistId = Long.MAX_VALUE;

        // when
        var result = subscriptionRepository.findByIdWithLock(notExistId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("구독 조회 시 비관적 쓰기 잠금이 적용된다.")
    void findByIdWithLock_pessimisticWrite_test() {
        // given
        ShopSubscription subscription = subscriptionRepository.save(
            createSubscription(
                302L,
                LocalDate.now().plusDays(30),
                ShopSubscriptionStatus.ACTIVE
            )
        );

        Long subscriptionId = subscription.getId();

        em.flush();
        em.clear();

        // when
        ShopSubscription result = subscriptionRepository
            .findByIdWithLock(subscriptionId)
            .orElseThrow();

        // then
        assertThat(em.getLockMode(result))
            .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("가게 ID로 유효한 ACTIVE 구독만 조회한다.")
    void findActiveByShopIdWithLock_filter_test() {
        // given
        Long shopId = 401L;
        LocalDate today = LocalDate.now();

        ShopSubscription active = subscriptionRepository.save(
            createSubscription(
                shopId, today.plusDays(10), ShopSubscriptionStatus.ACTIVE
            )
        );

        ShopSubscription todayActive = subscriptionRepository.save(
            createSubscription(
                shopId, today, ShopSubscriptionStatus.ACTIVE
            )
        );

        subscriptionRepository.save(
            createSubscription(
                shopId, today.minusDays(1), ShopSubscriptionStatus.ACTIVE
            )
        );

        subscriptionRepository.save(
            createSubscription(
                shopId, today.plusDays(30), ShopSubscriptionStatus.EXPIRED
            )
        );

        subscriptionRepository.save(
            createSubscription(
                402L, today.plusDays(30), ShopSubscriptionStatus.ACTIVE
            )
        );

        em.flush();
        em.clear();

        // when
        List<ShopSubscription> result =
            subscriptionRepository.findActiveByShopIdWithLock(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                today
            );

        // then
        assertThat(result)
            .extracting(ShopSubscription::getId)
            .containsExactly(todayActive.getId(), active.getId());
    }

    @Test
    @DisplayName("가게의 활성 구독을 ID 내림차순으로 조회한다.")
    void findActiveByShopIdWithLock_order_test() {
        // given
        Long shopId = 403L;
        LocalDate today = LocalDate.now();

        ShopSubscription first = subscriptionRepository.save(
            createSubscription(
                shopId, today.plusDays(10), ShopSubscriptionStatus.ACTIVE
            )
        );

        ShopSubscription second = subscriptionRepository.save(
            createSubscription(
                shopId, today.plusDays(20), ShopSubscriptionStatus.ACTIVE
            )
        );

        ShopSubscription third = subscriptionRepository.save(
            createSubscription(
                shopId, today.plusDays(30), ShopSubscriptionStatus.ACTIVE
            )
        );

        em.flush();
        em.clear();

        // when
        List<ShopSubscription> result =
            subscriptionRepository.findActiveByShopIdWithLock(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                today
            );

        // then
        assertThat(result)
            .extracting(ShopSubscription::getId)
            .containsExactly(
                third.getId(),
                second.getId(),
                first.getId()
            );
    }
    @Test
    @DisplayName("활성 구독 조회 시 비관적 쓰기 잠금이 적용된다.")
    void findActiveByShopIdWithLock_pessimisticWrite_test() {
        // given
        Long shopId = 404L;

        ShopSubscription subscription = subscriptionRepository.save(
            createSubscription(
                shopId,
                LocalDate.now().plusDays(30),
                ShopSubscriptionStatus.ACTIVE
            )
        );

        em.flush();
        em.clear();

        // when
        List<ShopSubscription> result =
            subscriptionRepository.findActiveByShopIdWithLock(
                shopId,
                ShopSubscriptionStatus.ACTIVE,
                LocalDate.now()
            );

        // then
        assertThat(result).hasSize(1);

        assertThat(em.getLockMode(result.get(0)))
            .isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }


    @Test
    @DisplayName("이미 등록된 결제 ID이면 true를 반환한다.")
    void existsByPaymentId_exists_test() {
        // given
        ShopSubscription subscription = subscriptionRepository.save(
            createSubscription(
                501L,
                LocalDate.now().plusDays(30),
                ShopSubscriptionStatus.ACTIVE
            )
        );

        String paymentId = subscription.getPaymentId();

        em.flush();
        em.clear();

        // when
        boolean result = subscriptionRepository.existsByPaymentId(paymentId);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("등록되지 않은 결제 ID이면 false를 반환한다.")
    void existsByPaymentId_notFound_test() {
        // given
        String paymentId = "PAY_NOT_EXIST";

        // when
        boolean result = subscriptionRepository.existsByPaymentId(paymentId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("소프트 삭제된 구독의 결제 ID는 존재하지 않는 것으로 조회된다.")
    void existsByPaymentId_deleted_test() {
        // given
        ShopSubscription subscription = createSubscription(
            503L,
            LocalDate.now().plusDays(30),
            ShopSubscriptionStatus.ACTIVE
        );

        subscription.delete();
        subscriptionRepository.save(subscription);

        String paymentId = subscription.getPaymentId();

        em.flush();
        em.clear();

        // when
        boolean result = subscriptionRepository.existsByPaymentId(paymentId);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("구독 ID로 조회하면 해당 가게 ID를 반환한다.")
    void findShopIdById_success_test() {
        // given
        Long shopId = 601L;

        ShopSubscription subscription = subscriptionRepository.save(
            createSubscription(
                shopId,
                LocalDate.now().plusDays(30),
                ShopSubscriptionStatus.ACTIVE
            )
        );

        em.flush();
        em.clear();

        // when
        Long result = subscriptionRepository
            .findShopIdById(subscription.getId())
            .orElseThrow();

        // then
        assertThat(result).isEqualTo(shopId);
    }

    @Test
    @DisplayName("존재하지 않는 구독 ID로 조회하면 빈 Optional을 반환한다.")
    void findShopIdById_notFound_test() {
        // given
        Long notExistId = Long.MAX_VALUE;

        // when
        var result = subscriptionRepository.findShopIdById(notExistId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("소프트 삭제된 구독 ID로 조회하면 빈 Optional을 반환한다.")
    void findShopIdById_deleted_test() {
        // given
        ShopSubscription subscription = createSubscription(
            603L,
            LocalDate.now().plusDays(30),
            ShopSubscriptionStatus.ACTIVE
        );

        subscription.delete();
        subscriptionRepository.save(subscription);

        em.flush();
        em.clear();

        // when
        var result = subscriptionRepository.findShopIdById(subscription.getId());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("가게 ID로 소프트 삭제된 구독을 조회한다.")
    void findDeletedByShopId_success_test() {
        // given
        Long shopId = 701L;

        ShopSubscription subscription = createSubscription(
            shopId,
            LocalDate.now().plusDays(30),
            ShopSubscriptionStatus.ACTIVE
        );

        subscription.delete();
        subscriptionRepository.save(subscription);

        em.flush();
        em.clear();

        // when
        ShopSubscription result = subscriptionRepository
            .findDeletedByShopId(shopId)
            .orElseThrow();

        // then
        assertThat(result.getShopId()).isEqualTo(shopId);
        assertThat(result.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("삭제된 구독이 없으면 빈 Optional을 반환한다.")
    void findDeletedByShopId_notFound_test() {
        // given
        Long shopId = 702L;

        // when
        var result = subscriptionRepository.findDeletedByShopId(shopId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제되지 않은 구독만 존재하면 빈 Optional을 반환한다.")
    void findDeletedByShopId_active_test() {
        // given
        Long shopId = 703L;

        subscriptionRepository.save(
            createSubscription(
                shopId,
                LocalDate.now().plusDays(30),
                ShopSubscriptionStatus.ACTIVE
            )
        );

        em.flush();
        em.clear();

        // when
        var result = subscriptionRepository.findDeletedByShopId(shopId);

        // then
        assertThat(result).isEmpty();
    }

}
