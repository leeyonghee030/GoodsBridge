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

    // 가게의 가장 최근 구독 이력 조회
    Optional<ShopSubscription> findFirstByShopIdOrderByIdDesc(Long shopId);

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

    // 💡 [추가] 구독 ID 기반 비관적 락(Pessimistic Write) 조회 (만료 프로세서 직렬화용)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShopSubscription s WHERE s.id = :id")
    Optional<ShopSubscription> findByIdWithLock(@Param("id") Long id);

    // 유효한 ACTIVE 구독 조회 및 비관적 락
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT s FROM ShopSubscription s
    WHERE s.shopId = :shopId
      AND s.status = :status
      AND s.endDate >= :today
    ORDER BY s.id DESC
    """)
    List<ShopSubscription> findActiveByShopIdWithLock(
        @Param("shopId") Long shopId,
        @Param("status") ShopSubscriptionStatus status,
        @Param("today") LocalDate today
    );

    // 소프트 삭제된 구독 조회용 쿼리 (Native Query)
    @Query(value = "SELECT * FROM shop_subscription_tb WHERE shop_id = :shopId AND deleted_at IS NOT NULL LIMIT 1", nativeQuery = true)
    Optional<ShopSubscription> findDeletedByShopId(@Param("shopId") Long shopId);

    // 중복 결제 방어를 위한 paymentId 존재 여부 확인
    boolean existsByPaymentId(String paymentId);



    boolean existsByShopIdAndStatusAndEndDateGreaterThanEqual(
        Long shopId,
        ShopSubscriptionStatus status,
        LocalDate today
    );
}
