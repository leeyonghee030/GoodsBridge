package com.goodsbridge.shop;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionRepository extends JpaRepository<ShopSubscription, Long> {
    Optional<ShopSubscription> findByShopId(Long shopId);

    // 구독 기간이 만료시 상태가 ACTIVE로 남아있는 가게 찾아내서 만료 처리 -> 상품들을 판매 중지 변경위해 만듬
    // 페이징(Pageable)을 적용하여 대용량 데이터를 청크 단위로 조회
    @Query("""
        SELECT s FROM ShopSubscription s
        WHERE s.endDate < :today AND s.status = 'ACTIVE' AND s.deletedAt IS NULL
        """)
    List<ShopSubscription> findAllExpiredActiveSubscriptions(
        @Param("today") LocalDate today,
        Pageable pageable
    );

    // 2. [추가] 구독 연장/결제 시 동시성 제어를 위한 비관적 락 (Pessimistic Write) 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM ShopSubscription s WHERE s.shopId = :shopId AND s.status = 'ACTIVE'")
    Optional<ShopSubscription> findActiveByShopIdWithLock(@Param("shopId") Long shopId);

    // 소프트 삭제된 구독 조회용 쿼리 (필요시 shopId 기준 등)
    @Query(value = "SELECT * FROM shop_subscription_tb WHERE shop_id = :shopId AND deleted_at IS NOT NULL LIMIT 1", nativeQuery = true)
    Optional<ShopSubscription> findDeletedByShopId(@Param("shopId") Long shopId);

    // [중복 방어] 이미 사용된 paymentId가 있는지 확인하기 위한 메서드
    boolean existsByPaymentId(String paymentId);
}
