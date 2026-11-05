package com.goodsbridge.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShopSubscriptionRepository extends JpaRepository<ShopSubscription, Long> {
    Optional<ShopSubscription> findByShopId(Long shopId);

    // 구독 기간이 만료시 상태가 ACTIVE로 남아있는 가게 찾아내서 만료 처리 -> 상품들을 판매 중지 변경위해 만듬
    // TODO - 스케줄러 통해서 자동으로 변경하지 않을거면 추후 삭제
    @Query("""
SELECT s FROM ShopSubscription s
WHERE s.endDate < :today AND s.status = 'ACTIVE'
""")
    List<ShopSubscription> findAllExpiredActiveSubscriptions(@Param("today")LocalDate today);

    // [중복 방어] 이미 사용된 paymentId가 있는지 확인하기 위한 메서드
    boolean existsByPaymentId(String paymentId);
}
