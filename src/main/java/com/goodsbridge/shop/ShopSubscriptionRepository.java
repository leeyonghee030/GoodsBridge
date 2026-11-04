package com.goodsbridge.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionRepository extends JpaRepository<ShopSubscription, Long> {
    Optional<ShopSubscription> findByShopId(Long shopId);

    @Query("""
SELECT s FROM ShopSubscription s
WHERE s.endDate < :today AND s.status = 'ACTIVE'
""")
    List<ShopSubscription> findAllExpiredActiveSubscriptions(@Param("today")LocalDate today);
}
