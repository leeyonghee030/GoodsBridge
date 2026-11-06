package com.goodsbridge.shop;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionRepository extends JpaRepository<ShopSubscription, Long> {

    Optional<ShopSubscription> findByShopId(Long shopId);

    /**
     * 💡 [ID 커서 페이징 쿼리]
     * 마지막으로 처리한 subscription ID보다 큰 데이터 중 만료된 ACTIVE 구독을 조회합니다.
     */
    @Query("""
        SELECT s FROM ShopSubscription s
        WHERE s.id > :lastId
          AND s.endDate < :today
          AND s.status = 'ACTIVE'
          AND s.deletedAt IS NULL
        ORDER BY s.id ASC
        """)
    List<ShopSubscription> findExpiredActiveSubscriptionsAfterId(
        @Param("lastId") Long lastId,
        @Param("today") LocalDate today,
        Pageable pageable
    );

    // 구독 연장/결제 시 동시성 제어를 위한 비관적 락 (Pessimistic Write) 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShopSubscription s WHERE s.shopId = :shopId AND s.status = 'ACTIVE'")
    Optional<ShopSubscription> findActiveByShopIdWithLock(@Param("shopId") Long shopId);

    // 소프트 삭제된 구독 조회용 쿼리 (Native Query)
    @Query(value = "SELECT * FROM shop_subscription_tb WHERE shop_id = :shopId AND deleted_at IS NOT NULL LIMIT 1", nativeQuery = true)
    Optional<ShopSubscription> findDeletedByShopId(@Param("shopId") Long shopId);

    // 중복 결제 방어를 위한 paymentId 존재 여부 확인
    boolean existsByPaymentId(String paymentId);

    /**
     * 소프트 삭제된(deleted_at이 채워진) 데이터도 무시하지 않고 ID로 안전하게 조회
     */
    @Query(value = "SELECT * FROM shop_subscription_tb WHERE id = :id", nativeQuery = true)
    Optional<ShopSubscription> findByIdIgnoreRestriction(@Param("id") Long id);
}
